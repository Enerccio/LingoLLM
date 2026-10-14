package com.github.enerccio.lingollm.crud;

import com.github.enerccio.lingollm.Configuration;
import com.github.enerccio.lingollm.domain.security.PersistedLoginInfo;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.test.LingoLLMTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.io.File;
import java.nio.file.Files;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserCrudTest extends LingoLLMTestBase {

    @Autowired
    private Configuration configuration;

    @Test
    void createAndFind() throws Exception {
        String login = uniqueName("alice");
        User user = createUser(login, "secret", false);

        assertThat(user.getId()).isNotNull();
        assertThat(user.getUuid()).hasSize(36);
        assertThat(user.getPasswordHash()).doesNotContain("secret").contains(":");

        User loaded = userService.findByName(login);
        assertThat(loaded.getId()).isEqualTo(user.getId());
        assertThat(loaded.getFullName()).isEqualTo(login);
        assertThat(loaded.isAdmin()).isFalse();
        assertThat(userService.existsUsers()).isTrue();
        assertThat(userService.findByName(login + "-missing")).isNull();
    }

    @Test
    void update() throws Exception {
        User user = createUser();
        Date modified = user.getModification();
        Thread.sleep(5);

        User loaded = userService.find(user.getId());
        loaded.setFullName("Alice Liddell");
        loaded.setAdmin(true);
        userService.save(loaded);

        User reloaded = userService.find(user.getId());
        assertThat(reloaded.getFullName()).isEqualTo("Alice Liddell");
        assertThat(reloaded.isAdmin()).isTrue();
        assertThat(reloaded.getModification()).isAfter(modified);
    }

    @Test
    void authenticate() throws Exception {
        User user = createUser(uniqueName("bob"), "correct horse", false);

        assertThat(userService.authenticate(user.getLogin(), "correct horse")).isTrue();
        assertThat(userService.authenticate(user.getLogin(), "wrong")).isFalse();
        assertThat(userService.authenticate(user.getLogin(), "")).isFalse();
        assertThat(userService.authenticate(user.getLogin(), null)).isFalse();
        assertThat(userService.authenticate(user.getLogin() + "x", "correct horse")).isFalse();
    }

    @Test
    void loginBackoffAfterFreeAttempts() throws Exception {
        User user = createUser(uniqueName("erin"), "pw", false);

        for (int i = 0; i < 10; i++) {
            assertThat(userService.authenticate(user.getLogin(), "bad")).isFalse();
        }
        assertThat(userService.find(user.getId()).getLockedUntil()).isZero();

        // 11th failure starts the lockout, the right password is refused while it lasts
        assertThat(userService.authenticate(user.getLogin(), "bad")).isFalse();
        assertThat(userService.find(user.getId()).getLockedUntil()).isGreaterThan(System.currentTimeMillis());
        assertThat(userService.authenticate(user.getLogin(), "pw")).isFalse();

        // lockout elapsed
        User locked = userService.find(user.getId());
        locked.setLockedUntil(System.currentTimeMillis() - 1);
        userService.save(locked);
        assertThat(userService.authenticate(user.getLogin(), "pw")).isTrue();
        assertThat(userService.find(user.getId()).getFailedLogins()).isZero();
    }

    @Test
    void addressBackoffAcrossUsers() throws Exception {
        String address = "203.0.113." + (int) (Math.random() * 200);
        User victim = createUser(uniqueName("frank"), "pw", false);

        // failures against unknown users count too, the user itself is never locked by them
        for (int i = 0; i < 30; i++) {
            assertThat(userService.authenticate(uniqueName("nobody"), "bad", address)).isFalse();
        }
        // 30 failures are free, the right password still works...
        assertThat(userService.authenticate(victim.getLogin(), "pw", address)).isTrue();

        // ...but the success does not start the count again, otherwise any account holder could reset it
        assertThat(userService.authenticate(uniqueName("nobody"), "bad", address)).isFalse();

        // the address is blocked even for the right password, but the user is not affected elsewhere
        assertThat(userService.authenticate(victim.getLogin(), "pw", address)).isFalse();
        assertThat(userService.find(victim.getId()).getFailedLogins()).isZero();
        assertThat(userService.authenticate(victim.getLogin(), "pw", address + "1")).isTrue();
        assertThat(userService.authenticate(victim.getLogin(), "pw")).isTrue();
    }

    @Test
    void changePassword() throws Exception {
        User user = createUser(uniqueName("carol"), "old", false);
        String oldHash = user.getPasswordHash();

        userService.changePassword(userService.find(user.getId()), "new");

        assertThat(userService.find(user.getId()).getPasswordHash()).isNotEqualTo(oldHash);
        assertThat(userService.authenticate(user.getLogin(), "new")).isTrue();
        assertThat(userService.authenticate(user.getLogin(), "old")).isFalse();
    }

    @Test
    void samePasswordGetsDifferentSalt() throws Exception {
        User a = createUser(uniqueName("a"), "same", false);
        User b = createUser(uniqueName("b"), "same", false);

        assertThat(a.getPasswordHash()).isNotEqualTo(b.getPasswordHash());
    }

    @Test
    void loginAvailability() throws Exception {
        User user = createUser();

        assertThat(userService.isLoginAvailable(null, user.getLogin())).isFalse();
        assertThat(userService.isLoginAvailable(new User(), user.getLogin())).isFalse();
        assertThat(userService.isLoginAvailable(user, user.getLogin())).isTrue();
        assertThat(userService.isLoginAvailable(null, uniqueName("free"))).isTrue();
    }

    @Test
    void deletedUserCannotLogInAndFreesLogin() throws Exception {
        loginAdmin();
        User user = createUser(uniqueName("dave"), "pw", false);
        String login = user.getLogin();

        userService.deleteUser(user);

        User deleted = userService.find(user.getId());
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getLogin()).isNotEqualTo(login).endsWith("#" + user.getUuid());
        assertThat(userService.findByName(login)).isNull();
        assertThat(userService.authenticate(login, "pw")).isFalse();
        assertThat(userService.isLoginAvailable(null, login)).isTrue();

        User reused = createUser(login, "pw2", false);
        assertThat(reused.getId()).isNotEqualTo(user.getId());
        assertThat(userService.authenticate(login, "pw2")).isTrue();
    }

    @Test
    void deletedUserDataFolderIsKeptUnderDeletedName() throws Exception {
        loginAdmin();
        String login = uniqueName("erin");
        User user = createUser(login, "pw", false);
        File folder = configuration.getUserDataFolder(user);
        Files.writeString(new File(folder, "note.txt").toPath(), "first");

        userService.deleteUser(user);

        File deleted = new File(configuration.getDataFolder(), login + "-deleted");
        assertThat(folder).doesNotExist();
        assertThat(new File(deleted, "note.txt")).hasContent("first");

        // second user with the same login gets a fresh folder, its deletion does not overwrite the first one
        User reused = createUser(login, "pw", false);
        assertThat(configuration.getUserDataFolder(reused).list()).isEmpty();
        Files.writeString(new File(configuration.getUserDataFolder(reused), "note.txt").toPath(), "second");
        userService.deleteUser(reused);

        assertThat(new File(deleted, "note.txt")).hasContent("first");
        assertThat(new File(configuration.getDataFolder(), login + "-deleted-2/note.txt")).hasContent("second");
    }

    @Test
    void deletingUserTwiceIsNoop() throws Exception {
        loginAdmin();
        User user = createUser();
        userService.deleteUser(user);
        String renamed = userService.find(user.getId()).getLogin();

        userService.deleteUser(userService.find(user.getId()));

        assertThat(userService.find(user.getId()).getLogin()).isEqualTo(renamed);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void lastAdminIsProtected() throws Exception {
        User admin = loginAs(createUser(uniqueName("admin"), "pw", true));
        User regular = createUser();

        assertThat(userService.isLastAdmin(admin)).isTrue();
        assertThat(userService.isLastAdmin(regular)).isFalse();
        assertThatThrownBy(() -> userService.deleteUser(admin)).isInstanceOf(IllegalStateException.class);
        assertThat(userService.find(admin.getId()).isDeleted()).isFalse();

        User second = createUser(uniqueName("admin"), "pw", true);
        assertThat(userService.isLastAdmin(admin)).isFalse();
        userService.deleteUser(admin);
        assertThat(userService.find(admin.getId()).isDeleted()).isTrue();
        assertThat(userService.isLastAdmin(second)).isTrue();
    }

    @Test
    void rememberMeLoginRotatesSecret() throws Exception {
        User user = userService.find(createUser().getId());
        PersistedLoginInfo info = userService.generateNewPersistentInfo(user);
        userService.addPersistedLoginInfo(user, info);
        userService.save(user);

        User loaded = userService.find(user.getId());
        assertThat(userService.getPersistedLoginInfo(loaded)).extracting(PersistedLoginInfo::getIdentifier)
                .containsExactly(info.getIdentifier());

        PersistedLoginInfo rotated = userService.authenticateFromCookie(loaded, info.getIdentifier(), info.getPlainSecret());
        assertThat(rotated).isNotNull();
        assertThat(rotated.getIdentifier()).isEqualTo(info.getIdentifier());
        assertThat(rotated.getPlainSecret()).isNotEqualTo(info.getPlainSecret());

        // old secret is no longer valid, new one is
        User afterRotation = userService.find(user.getId());
        assertThat(userService.authenticateFromCookie(afterRotation, info.getIdentifier(), info.getPlainSecret())).isNull();
        assertThat(userService.authenticateFromCookie(userService.find(user.getId()), rotated.getIdentifier(), rotated.getPlainSecret()))
                .isNotNull();
    }

    @Test
    void rememberMeRejectsUnknownAndForeignCookies() throws Exception {
        User user = userService.find(createUser().getId());
        PersistedLoginInfo info = userService.generateNewPersistentInfo(user);
        userService.addPersistedLoginInfo(user, info);
        userService.save(user);
        User other = userService.find(createUser().getId());

        assertThat(userService.authenticateFromCookie(userService.find(user.getId()), "unknown", info.getPlainSecret())).isNull();
        assertThat(userService.authenticateFromCookie(userService.find(user.getId()), info.getIdentifier(), "wrong")).isNull();
        assertThat(userService.authenticateFromCookie(other, info.getIdentifier(), info.getPlainSecret())).isNull();
    }

    @Test
    void expiredRememberMeLoginIsRemoved() throws Exception {
        User user = userService.find(createUser().getId());
        PersistedLoginInfo info = userService.generateNewPersistentInfo(user);
        PersistedLoginInfo fresh = userService.generateNewPersistentInfo(user);
        // stored format: identifier;hashedSecret;created;lastAccess, the first one created long ago
        user.setSavedLogins(info.getIdentifier() + ";" + info.getHashedSecret() + ";0;0|"
                + fresh.getIdentifier() + ";" + fresh.getHashedSecret() + ";" + fresh.getCreate() + ";" + fresh.getLastAccess());
        userService.save(user);

        assertThat(userService.authenticateFromCookie(userService.find(user.getId()), info.getIdentifier(), info.getPlainSecret())).isNull();
        assertThat(userService.getPersistedLoginInfo(userService.find(user.getId()))).extracting(PersistedLoginInfo::getIdentifier)
                .containsExactly(fresh.getIdentifier());
    }

    @Test
    void changingPasswordEndsRememberMeLogins() throws Exception {
        User user = userService.find(createUser().getId());
        PersistedLoginInfo info = userService.generateNewPersistentInfo(user);
        userService.addPersistedLoginInfo(user, info);
        userService.save(user);

        userService.changePassword(userService.find(user.getId()), "new");

        User loaded = userService.find(user.getId());
        assertThat(userService.getPersistedLoginInfo(loaded)).isEmpty();
        assertThat(userService.authenticateFromCookie(loaded, info.getIdentifier(), info.getPlainSecret())).isNull();
    }

    @Test
    void clearedPasswordLetsUserLogInWithoutOneUntilTheySetNew() throws Exception {
        loginAdmin();
        User user = createUser(uniqueName("frank"), "forgotten", false);
        userService.addPersistedLoginInfo(user, userService.generateNewPersistentInfo(user));
        userService.save(user);

        userService.clearPassword(userService.find(user.getId()));

        User loaded = userService.find(user.getId());
        assertThat(loaded.getPasswordHash()).isNull();
        assertThat(userService.getPersistedLoginInfo(loaded)).isEmpty();
        assertThat(userService.authenticate(user.getLogin(), "forgotten")).isFalse();
        assertThat(userService.authenticate(user.getLogin(), "")).isTrue();

        userService.changePassword(loaded, "new");
        assertThat(userService.authenticate(user.getLogin(), "")).isFalse();
        assertThat(userService.authenticate(user.getLogin(), "new")).isTrue();
    }

    @Test
    void deletedUserLosesRememberMeLogins() throws Exception {
        loginAdmin();
        User user = userService.find(createUser().getId());
        userService.addPersistedLoginInfo(user, userService.generateNewPersistentInfo(user));
        userService.save(user);

        userService.deleteUser(user);

        assertThat(userService.getPersistedLoginInfo(userService.find(user.getId()))).isEmpty();
    }

    @Test
    void onlyAdministratorsDeleteClearOrUnlockUsers() throws Exception {
        User target = createUser();
        login();

        assertThatThrownBy(() -> userService.deleteUser(target)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> userService.clearPassword(target)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> userService.unlock(target)).isInstanceOf(SecurityException.class);
        assertThat(userService.find(target.getId()).isDeleted()).isFalse();

        currentUser.setId(null);
        assertThatThrownBy(() -> userService.deleteUser(target)).isInstanceOf(SecurityException.class);
    }

    @Test
    void sessionCopyOfAdminFlagIsNotTrusted() throws Exception {
        User admin = loginAdmin();
        User target = createUser();
        admin.setAdmin(false);
        userService.save(admin);
        // demoted in the database, the session still says admin
        currentUser.setAdmin(true);

        assertThatThrownBy(() -> userService.deleteUser(target)).isInstanceOf(SecurityException.class);
    }
}
