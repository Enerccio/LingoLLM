package com.github.enerccio.lingollm.domain.security.service.impl;

import com.github.enerccio.lingollm.bound.SessionManager;
import com.github.enerccio.lingollm.domain.security.AdminGuard;
import com.github.enerccio.lingollm.domain.security.PersistedLoginInfo;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.security.repository.UserRepository;
import com.github.enerccio.lingollm.domain.security.service.UserService;
import com.github.enerccio.lingollm.domain.service.impl.BaseServiceImpl;
import com.github.enerccio.lingollm.domain.traits.CommonTx;
import com.github.enerccio.lingollm.domain.traits.CommonTxReadOnly;
import com.github.enerccio.lingollm.domain.traits.NoTx;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class UserServiceImpl extends BaseServiceImpl<User, UserRepository> implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    static final int FREE_LOGIN_ATTEMPTS = 10;
    private static final long MAX_BACKOFF_MILLIS = 15 * 60 * 1000L;
    // used to burn the same hashing time when the user or its password does not exist
    private static final String DUMMY_SALT = Base64.getEncoder().encodeToString(new byte[16]);
    private static final String DUMMY_HASH = "";

    // per client address throttling, kept in memory only (a restart clears it); more free attempts than per user as
    // several users can share an address (NAT, reverse proxy). A success does not clear the count - whoever owns any
    // account could otherwise reset it between batches of guesses - failures are forgotten ADDRESS_FORGET_MILLIS after
    // the last one. The table is bounded, the least recently used address is dropped first.
    static final int FREE_ADDRESS_ATTEMPTS = 30;
    static final int MAX_TRACKED_ADDRESSES = 10_000;
    private static final long ADDRESS_FORGET_MILLIS = 60 * 60 * 1000L;
    private static final long ADDRESS_PURGE_INTERVAL_MILLIS = 60 * 1000L;
    private final Map<String, AddressFailures> addressFailures = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, AddressFailures> eldest) {
            return size() > MAX_TRACKED_ADDRESSES;
        }
    };
    private long lastAddressPurge;

    private static final class AddressFailures {
        int failed;
        long lockedUntil;
        long lastFailure;
    }

    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private AdminGuard adminGuard;

    @Override
    @CommonTxReadOnly
    public boolean existsUsers() throws Exception {
        return getRepository().existsUsers();
    }

    @Override
    @CommonTxReadOnly
    public User findByName(String name) throws Exception {
        return find(getRepository().findByName(name));
    }

    @Override
    @CommonTxReadOnly
    public boolean isLoginAvailable(User user, String login) throws Exception {
        User existing = findByName(login);
        return existing == null || (user != null && existing.getId().equals(user.getId()));
    }

    @Override
    @CommonTxReadOnly
    public boolean isLastAdmin(User user) throws Exception {
        User current = find(user);
        return current != null && current.isAdmin() && getRepository().countAdmins() <= 1;
    }

    @Override
    @CommonTx
    public void deleteUser(User user) throws Exception {
        adminGuard.requireAdmin();
        User current = find(user);
        if (current == null || current.isDeleted()) {
            return;
        }
        if (isLastAdmin(current)) {
            throw new IllegalStateException("Cannot delete last administrator");
        }
        String login = current.getLogin();
        // free the unique login for reuse and drop all remembered logins
        current.setLogin(StringUtils.left(login, 27) + "#" + current.getUuid());
        current.setSavedLogins(null);
        delete(current, false);
        retireDataFolder(login);
    }

    /**
     * Data folder is named by login, it is moved away so a new user with the same login does not inherit the files.
     */
    private void retireDataFolder(String login) {
        File folder = new File(configuration.getDataFolder(), login);
        if (!folder.exists()) {
            return;
        }
        File target = new File(configuration.getDataFolder(), login + "-deleted");
        for (int i = 2; target.exists(); i++) {
            target = new File(configuration.getDataFolder(), login + "-deleted-" + i);
        }
        try {
            Files.move(folder.toPath(), target.toPath());
        } catch (IOException e) {
            log.warn("Failed to move data folder of deleted user {} to {}", login, target, e);
        }
    }

    /**
     * Password check with a constant amount of work: a hash is computed whether or not the user exists, has a
     * password or is locked, and there are no early returns, so timing does not reveal which case it was. After
     * {@link #FREE_LOGIN_ATTEMPTS} failures the user is locked out for an exponentially growing time. The same
     * applies to a client address after {@link #FREE_ADDRESS_ATTEMPTS} failures (any user name), tracked in memory.
     */
    @Override
    @CommonTx
    public boolean authenticate(String username, String password) throws Exception {
        return authenticate(username, password, null);
    }

    @Override
    @CommonTx
    public boolean authenticate(String username, String password, String clientAddress) throws Exception {
        User user = findByName(username);
        long now = System.currentTimeMillis();
        boolean addressLocked = isAddressLocked(clientAddress, now);

        String storedPasswordData = user == null ? null : user.getPasswordHash();
        String[] parts = storedPasswordData == null ? new String[0] : storedPasswordData.split(":");
        boolean wellFormed = parts.length == 2;
        String salt = wellFormed ? parts[0] : DUMMY_SALT;
        String expected = wellFormed ? parts[1] : DUMMY_HASH;

        boolean passwordGiven = StringUtils.isNotBlank(password);
        String computed = hashPassword(passwordGiven ? password : "", salt);
        boolean hashMatches = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), computed.getBytes(StandardCharsets.UTF_8));

        boolean noPasswordSet = user != null && StringUtils.isBlank(storedPasswordData);
        boolean ok = user != null
                && ((noPasswordSet && !passwordGiven) || (wellFormed && passwordGiven && hashMatches));
        boolean locked = user != null && user.getLockedUntil() > now;

        if (addressLocked) {
            // not counted against the user, otherwise a blocked address could keep extending their lockout; not logged
            // as a warning either, the block itself was
            log.debug("Login attempt for {} from blocked address {}", username, clientAddress);
            return false;
        }

        if (user != null) {
            if (ok && !locked) {
                if (user.getFailedLogins() != 0 || user.getLockedUntil() != 0) {
                    user.setFailedLogins(0);
                    user.setLockedUntil(0);
                    save(user);
                }
            } else if (!locked) {
                int failed = user.getFailedLogins() + 1;
                user.setFailedLogins(failed);
                if (failed > FREE_LOGIN_ATTEMPTS) {
                    user.setLockedUntil(now + backoffMillis(failed - FREE_LOGIN_ATTEMPTS));
                }
                save(user);
                log.warn("Failed login for user {} from {} (consecutive failures: {})", username, clientAddress, failed);
            }
        } else {
            log.warn("Failed login for unknown user {} from {}", username, clientAddress);
        }
        if (!ok || locked) {
            recordAddressFailure(clientAddress, now);
        }
        return ok && !locked;
    }

    private synchronized boolean isAddressLocked(String address, long now) {
        AddressFailures failures = StringUtils.isBlank(address) ? null : addressFailures.get(address);
        return failures != null && failures.lockedUntil > now;
    }

    private synchronized void recordAddressFailure(String address, long now) {
        if (StringUtils.isBlank(address)) {
            return;
        }
        if (now - lastAddressPurge > ADDRESS_PURGE_INTERVAL_MILLIS) {
            lastAddressPurge = now;
            addressFailures.values().removeIf(f -> f.lastFailure + ADDRESS_FORGET_MILLIS < now && f.lockedUntil < now);
        }
        AddressFailures failures = addressFailures.computeIfAbsent(address, a -> new AddressFailures());
        if (failures.lastFailure + ADDRESS_FORGET_MILLIS < now && failures.lockedUntil < now) {
            failures.failed = 0;
        }
        failures.failed++;
        failures.lastFailure = now;
        if (failures.failed > FREE_ADDRESS_ATTEMPTS) {
            failures.lockedUntil = now + backoffMillis(failures.failed - FREE_ADDRESS_ATTEMPTS);
            log.warn("Address {} blocked from logging in until {} ({} consecutive failures)",
                    address, new Date(failures.lockedUntil), failures.failed);
        }
    }

    /** 1s, 2s, 4s ... capped at {@link #MAX_BACKOFF_MILLIS}. */
    private static long backoffMillis(int overLimit) {
        return Math.min(MAX_BACKOFF_MILLIS, 1000L << Math.min(overLimit - 1, 20));
    }

    @Override
    @CommonTxReadOnly
    public boolean canAutologin(String username) throws Exception {
        User user = StringUtils.isBlank(username) ? null : findByName(username);
        return user != null && !user.isDeleted() && StringUtils.isBlank(user.getPasswordHash())
                && user.getLockedUntil() <= System.currentTimeMillis();
    }

    @Override
    @CommonTx
    public User changePassword(User user, String password) throws Exception {
        byte[] saltBytes = new byte[16];
        secureRandom.nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);
        String hash = hashPassword(password, salt);
        user.setPasswordHash(salt + ":" + hash);
        user.setSavedLogins(null);
        user.setFailedLogins(0);
        user.setLockedUntil(0);
        save(user);
        sessionManager.runForUsers(u -> user.getId().equals(u.getId()), (sessionInformation) -> {
            sessionInformation.boundVaadinSession.getSession().invalidate();
        }, true, true);
        return user;
    }

    /**
     * Removes the password, the user then logs in with the user name only and sets a new password themselves.
     */
    @Override
    @CommonTx
    public User clearPassword(User user) throws Exception {
        adminGuard.requireAdmin();
        user.setPasswordHash(null);
        user.setSavedLogins(null);
        user.setFailedLogins(0);
        user.setLockedUntil(0);
        save(user);
        sessionManager.runForUsers(u -> user.getId().equals(u.getId()), (sessionInformation) -> {
            sessionInformation.boundVaadinSession.getSession().invalidate();
        }, true, true);
        return user;
    }

    /**
     * Ends a login lockout and forgets the failed attempts, an administrator's action.
     */
    @Override
    @CommonTx
    public User unlock(User user) throws Exception {
        adminGuard.requireAdmin();
        user.setFailedLogins(0);
        user.setLockedUntil(0);
        return save(user);
    }

    /**
     * Checks a remember-me cookie. The secret is hashed once and compared against every saved login with
     * {@link MessageDigest#isEqual}, without leaving the loop on a match, so timing does not reveal whether the
     * identifier exists or how much of the secret was right.
     */
    @Override
    @CommonTx
    public PersistedLoginInfo authenticateFromCookie(User user, String identifier, String secret) throws Exception {
        List<PersistedLoginInfo> loginInfos = user == null ? Collections.emptyList() : getPersistedLoginInfo(user);
        String computed = user == null ? DUMMY_HASH : hashPersistedLoginSecret(user, secret);
        byte[] computedBytes = computed.getBytes(StandardCharsets.UTF_8);
        byte[] identifierBytes = StringUtils.defaultString(identifier).getBytes(StandardCharsets.UTF_8);

        PersistedLoginInfo matched = null;
        for (PersistedLoginInfo loginInfo : loginInfos) {
            boolean identifierMatches = MessageDigest.isEqual(
                    loginInfo.getIdentifier().getBytes(StandardCharsets.UTF_8), identifierBytes);
            boolean secretMatches = MessageDigest.isEqual(
                    loginInfo.getHashedSecret().getBytes(StandardCharsets.UTF_8), computedBytes);
            if (identifierMatches & secretMatches) {
                matched = loginInfo;
            }
        }
        if (matched == null) {
            return null;
        }

        if (configuration.getPersistentLoginInfoTTL() != null) {
            long ctime = System.currentTimeMillis() - (configuration.getPersistentLoginInfoTTL() * 1000);
            if (ctime >= matched.getCreate()) {
                // we passed the max login window
                deletePersistedLoginInfo(user, matched);
                save(user);
                return null;
            }
        }
        PersistedLoginInfo newLoginInfo = generateNewPersistentInfo(user);
        newLoginInfo.setIdentifier(matched.getIdentifier());
        newLoginInfo.setCreate(matched.getCreate());
        deletePersistedLoginInfo(user, matched);
        addPersistedLoginInfo(user, newLoginInfo);
        save(user);
        return newLoginInfo;
    }

    @Override
    @NoTx
    public void deletePersistedLoginInfo(User user, PersistedLoginInfo persistedLoginInfo) throws Exception {
        List<PersistedLoginInfo> persistedLoginInfos = getPersistedLoginInfo(user);
        List<PersistedLoginInfo> newPersistedLoginInfos = new ArrayList<>();
        for (PersistedLoginInfo loginInfo : persistedLoginInfos) {
            if (loginInfo.getIdentifier().equals(persistedLoginInfo.getIdentifier()))
                continue;
            long ctime = System.currentTimeMillis() - configuration.getPersistentLoginInfoTTL() * 1000;
            if (ctime >= loginInfo.getCreate()) {
                continue;
            }
            newPersistedLoginInfos.add(loginInfo);
        }
        serializePersistedLoginInfo(user, newPersistedLoginInfos);
    }

    @Override
    @NoTx
    public List<PersistedLoginInfo> getPersistedLoginInfo(User user) throws Exception {
        if (StringUtils.isBlank(user.getSavedLogins()))
            return Collections.emptyList();
        LinkedHashSet<PersistedLoginInfo> loginInfos = new LinkedHashSet<>();
        for (String savedLogin : user.getSavedLogins().split(Pattern.quote("|"))) {
            PersistedLoginInfo info = new PersistedLoginInfo();
            String[] parsed = savedLogin.split(Pattern.quote(";"));
            info.setIdentifier(parsed[0]);
            info.setHashedSecret(parsed[1]);
            info.setCreate(Long.parseLong(parsed[2]));
            info.setLastAccess(Long.parseLong(parsed[3]));
            loginInfos.add(info);
        }
        return loginInfos.stream().toList();
    }

    @Override
    @NoTx
    public PersistedLoginInfo generateNewPersistentInfo(User user) throws Exception {
        BigInteger prime = BigInteger.probablePrime(160, secureRandom);
        String secret = "" + prime;
        PersistedLoginInfo loginInfo = new PersistedLoginInfo();
        loginInfo.setIdentifier(UUID.randomUUID().toString());
        loginInfo.setCreate(System.currentTimeMillis());
        loginInfo.setLastAccess(System.currentTimeMillis());
        loginInfo.setPlainSecret(secret);
        loginInfo.setHashedSecret(hashPersistedLoginSecret(user, secret));
        return loginInfo;
    }

    @Override
    @NoTx
    public void addPersistedLoginInfo(User user, PersistedLoginInfo persistedLoginInfo) throws Exception {
        List<PersistedLoginInfo> persistedLoginInfos = getPersistedLoginInfo(user);
        List<PersistedLoginInfo> newPersistedLoginInfos = new ArrayList<>();
        for (PersistedLoginInfo loginInfo : persistedLoginInfos) {
            if (loginInfo.getIdentifier().equals(persistedLoginInfo.getIdentifier()))
                continue;
            long ctime = System.currentTimeMillis() - (configuration.getPersistentLoginInfoTTL() * 1000);
            if (ctime >= loginInfo.getCreate()) {
                continue;
            }
            newPersistedLoginInfos.add(loginInfo);
        }
        newPersistedLoginInfos.add(persistedLoginInfo);
        serializePersistedLoginInfo(user, newPersistedLoginInfos);
    }

    protected String hashPersistedLoginSecret(User user, String secret) throws Exception {
        secret = user.getId() + "_" + secret;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(secret.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest.digest());
    }

    @NoTx
    protected void serializePersistedLoginInfo(User user, List<PersistedLoginInfo> persistedLoginInfos) throws Exception {
        user.setSavedLogins(persistedLoginInfos.stream().map(pli ->
                pli.getIdentifier() + ";" + pli.getHashedSecret() + ";" + pli.getCreate() + ";" + pli.getLastAccess()).collect(Collectors.joining("|")));
    }

    private String hashPassword(String password, String salt) throws Exception {
        char[] passwordChars = password.toCharArray();
        byte[] saltBytes = Base64.getDecoder().decode(salt);

        PBEKeySpec spec = new PBEKeySpec(passwordChars, saltBytes, ITERATIONS, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
        byte[] hash = factory.generateSecret(spec).getEncoded();

        return Base64.getEncoder().encodeToString(hash);
    }

}
