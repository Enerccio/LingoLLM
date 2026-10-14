package com.github.enerccio.lingollm;

import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.utils.ClientAddressResolver;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.sqlite.SQLiteDataSource;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.*;

public class Configuration implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(Configuration.class);

    public static final String PENDING_RESTORE_SUFFIX = ".restore";
    public static final String PRE_RESTORE_PREFIX = "pre-restore-";
    public static final String REJECTED_RESTORE_PREFIX = "rejected-restore-";
    public static final String PRE_MIGRATION_PREFIX = "pre-migration-";
    /**
     * How many copies made before a migration are kept, older ones are deleted.
     */
    private static final int PRE_MIGRATION_KEEP = 3;
    private static final String[] SQLITE_SIDE_FILES = {"-wal", "-shm"};
    public static final String SECRET_KEY_FILE = "secret.key";
    public static final String ENCRYPTED_PREFIX = "enc:";
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private final SecureRandom random = new SecureRandom();

    private Long persistentLoginInfoTTL = 60 * 60 * 24 * 30L;
    private boolean allowPersistentLogin = true;
    private ClientAddressResolver clientAddressResolver = new ClientAddressResolver(null);
    private File folder;
    private File dataFolder;
    private File databaseBackupFolder;
    private File databaseFile;
    private boolean saveAsyncStacks = false;
    private SecretKeySpec secretKey;

    public Long getPersistentLoginInfoTTL() {
        return persistentLoginInfoTTL;
    }

    public void setPersistentLoginInfoTTL(Long persistentLoginInfoTTL) {
        this.persistentLoginInfoTTL = persistentLoginInfoTTL;
    }

    public boolean isAllowPersistentLogin() {
        return allowPersistentLogin;
    }

    public void setAllowPersistentLogin(boolean allowPersistentLogin) {
        this.allowPersistentLogin = allowPersistentLogin;
    }

    /**
     * Reverse proxies whose {@code X-Forwarded-For} header is believed, comma separated addresses or CIDR ranges.
     * Empty (the default): the header is ignored and the client address is the one of the connection.
     */
    public void setTrustedProxies(String trustedProxies) {
        this.clientAddressResolver = new ClientAddressResolver(trustedProxies);
    }

    public ClientAddressResolver getClientAddressResolver() {
        return clientAddressResolver;
    }

    public File getFolder() {
        return folder;
    }

    /**
     * Overrides the application folder (default {@code ~/.lingollm}). Used by tests to keep data in a temp folder.
     */
    public void setFolder(File folder) {
        this.folder = folder;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (folder == null) {
            folder = new File(System.getProperty("user.home"), ".lingollm");
        }
        if (!folder.exists() && !folder.mkdirs()) {
            throw new RuntimeException("Cannot create folder " + folder.getAbsolutePath());
        }

        dataFolder = new File(folder, "data");
        if (!dataFolder.exists())
            dataFolder.mkdirs();

        databaseBackupFolder = new File(folder, "db-backups");
        if (!databaseBackupFolder.exists())
            databaseBackupFolder.mkdirs();

        secretKey = loadOrCreateSecretKey(new File(folder, SECRET_KEY_FILE));
    }

    /**
     * Key for secrets stored in the database (API keys). Generated on the first start and kept outside the database,
     * so database backups alone don't reveal the secrets.
     */
    private SecretKeySpec loadOrCreateSecretKey(File keyFile) throws IOException, GeneralSecurityException {
        if (!keyFile.exists()) {
            Files.writeString(keyFile.toPath(), UUID.randomUUID().toString(), StandardCharsets.UTF_8);
            log.info("Generated secret key {}", keyFile.getAbsolutePath());
        }
        String key = Files.readString(keyFile.toPath(), StandardCharsets.UTF_8).trim();
        if (key.isEmpty()) {
            throw new IllegalStateException("Secret key file " + keyFile.getAbsolutePath() + " is empty");
        }
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(hash, "AES");
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTED_PREFIX);
    }

    /**
     * Encrypts a secret with the installation key, returns {@code enc:<base64 of iv + ciphertext>}.
     */
    public String encrypt(String value) {
        if (value == null || value.isEmpty() || isEncrypted(value)) {
            return value;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(result);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot encrypt secret", e);
        }
    }

    /**
     * Decrypts a value from {@link #encrypt}. A value without the prefix is returned as it is (not migrated yet), a
     * value encrypted with another key (database from another installation) is returned as {@code null}.
     */
    public String decrypt(String value) {
        if (!isEncrypted(value)) {
            return value;
        }
        try {
            byte[] data = Base64.getDecoder().decode(value.substring(ENCRYPTED_PREFIX.length()));
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, data, 0, IV_LENGTH));
            return new String(cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.warn("Cannot decrypt secret, was it encrypted with a different {}?", SECRET_KEY_FILE, e);
            return null;
        }
    }

    public String resolveDb(String db) throws IOException {
        databaseFile = new File(folder, db);
        applyPendingRestore(databaseFile);
        backupBeforeMigration(databaseFile);
        return jdbcUrl(databaseFile);
    }

    /**
     * Flyway rolls back a migration that fails, but not one that succeeded and turned out wrong, and an older version
     * refuses a database from a newer one. So before an upgrade migrates an existing database, a consistent copy of
     * it is put in the backup folder (it shows up in Admin - Database Backups and can be restored from there). Done
     * before the connection pool exists, nothing else uses the file yet.
     * <p>
     * A copy that can't be made is logged, not fatal: a desktop user with a full disk should still get to the
     * application.
     */
    private void backupBeforeMigration(File db) {
        try {
            Optional<String> from = DatabaseCheck.pendingMigrationFrom(db);
            if (from.isEmpty()) {
                return;
            }
            String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            String prefix = PRE_MIGRATION_PREFIX + timestamp + "-V" + from.get() + "-";
            File target = new File(databaseBackupFolder, prefix + db.getName());
            for (int counter = 1; target.exists(); counter++) {
                target = new File(databaseBackupFolder, prefix + counter + "-" + db.getName());
            }
            try (Connection connection = DriverManager.getConnection(jdbcUrl(db));
                 Statement statement = connection.createStatement()) {
                statement.execute("VACUUM INTO '" + target.getAbsolutePath().replace("'", "''") + "'");
            }
            log.warn("The database is at version {} and is going to be migrated, a copy was saved to {}", from.get(),
                    target.getAbsolutePath());
            deleteOldPreMigrationCopies();
        } catch (Exception e) {
            log.error("Could not save a copy of the database before migrating it, continuing without it", e);
        }
    }

    private void deleteOldPreMigrationCopies() throws IOException {
        File[] copies = databaseBackupFolder.listFiles(f -> f.isFile() && f.getName().startsWith(PRE_MIGRATION_PREFIX)
                && f.getName().endsWith(".sqlite"));
        if (copies == null || copies.length <= PRE_MIGRATION_KEEP) {
            return;
        }
        // the names start with a sortable timestamp
        Arrays.sort(copies, Comparator.comparing(File::getName).reversed());
        for (int i = PRE_MIGRATION_KEEP; i < copies.length; i++) {
            Files.deleteIfExists(copies[i].toPath());
            log.info("Deleted old pre-migration database copy {}", copies[i].getName());
        }
    }

    private static String jdbcUrl(File db) {
        return "jdbc:sqlite:" + db.getAbsolutePath() + "?busy_timeout=10000&journal_mode=WAL";
    }

    /**
     * Restore can't be done on live database, so it is staged next to database file and swapped in before
     * datasource is created. Current database (with its WAL files) is kept in backup folder.
     * <p>
     * The staged file is checked first ({@link DatabaseCheck}) and migrated right after the swap. When it is not a
     * usable LingoLLM database or the migration fails, the previous database is put back, so the application still
     * starts, and the rejected file is kept in the backup folder as {@code rejected-restore-*}.
     */
    private void applyPendingRestore(File db) throws IOException {
        File pending = new File(db.getAbsolutePath() + PENDING_RESTORE_SUFFIX);
        if (!pending.exists()) {
            return;
        }
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File rejected = new File(databaseBackupFolder, REJECTED_RESTORE_PREFIX + timestamp + "-" + db.getName());

        try {
            DatabaseCheck.check(pending);
        } catch (DatabaseCheck.InvalidDatabaseException e) {
            Files.move(pending.toPath(), rejected.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.error("Database restore rejected, {}. The current database is kept, the rejected file was moved to {}",
                    e.getMessage(), rejected.getAbsolutePath());
            return;
        }

        File preRestore = new File(databaseBackupFolder, PRE_RESTORE_PREFIX + timestamp + "-" + db.getName());
        boolean hadDatabase = db.exists();
        if (hadDatabase) {
            moveWithSideFiles(db, preRestore);
        }
        Files.move(pending.toPath(), db.toPath(), StandardCopyOption.REPLACE_EXISTING);

        try {
            migrate(db);
        } catch (Exception e) {
            moveWithSideFiles(db, rejected);
            if (hadDatabase) {
                moveWithSideFiles(preRestore, db);
            }
            log.error("Restored database failed to migrate, the previous database was put back. The rejected file was "
                    + "moved to {}", rejected.getAbsolutePath(), e);
            return;
        }
        log.warn("Database restored from pending backup, previous database moved to {}", preRestore.getAbsolutePath());
    }

    /**
     * Migrates the restored database before the application uses it, so a failure can still be rolled back.
     */
    private void migrate(File db) throws SQLException {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl(jdbcUrl(db));
        Flyway flyway = new Flyway(DatabaseCheck.flywayConfiguration(dataSource));
        flyway.migrate();
        // the WAL is checkpointed and the files released when the last connection closes
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        }
    }

    private static void moveWithSideFiles(File from, File to) throws IOException {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
        for (String suffix : SQLITE_SIDE_FILES) {
            File sideFile = new File(from.getAbsolutePath() + suffix);
            File target = new File(to.getAbsolutePath() + suffix);
            if (sideFile.exists()) {
                Files.move(sideFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.deleteIfExists(target.toPath());
            }
        }
    }

    public File getDatabaseFile() {
        return databaseFile;
    }

    public File getDatabaseBackupFolder() {
        return databaseBackupFolder;
    }

    public File getDataFolder() {
        return dataFolder;
    }

    public File getUserDataFolder(User user) {
        File file = new File(dataFolder, user.getLogin());
        if (!file.exists())
            file.mkdirs();
        return file;
    }

    public File getImagesFolder(User user) {
        File file = new File(getUserDataFolder(user), "images");
        if (!file.exists())
            file.mkdirs();
        return file;
    }

    public File getResourcesFolder(User user) {
        File file = new File(getUserDataFolder(user), "resources");
        if (!file.exists())
            file.mkdirs();
        return file;
    }

    public boolean isSaveAsyncStacks() {
        return saveAsyncStacks;
    }

    public void setSaveAsyncStacks(boolean saveAsyncStacks) {
        this.saveAsyncStacks = saveAsyncStacks;
    }
}
