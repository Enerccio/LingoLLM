package com.github.enerccio.lingollm.db;

import com.github.enerccio.lingollm.Configuration;
import com.github.enerccio.lingollm.DatabaseCheck;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;

/**
 * A database is checked before it is restored, and a restore that fails on start puts the previous database
 * back instead of stopping the application.
 */
class DatabaseRestoreCheckTest {

    private static final String DB = "lingollm.sqlite";

    @TempDir
    Path tempDir;

    private Configuration configuration;

    @BeforeEach
    void setUp() throws Exception {
        configuration = new Configuration();
        configuration.setFolder(tempDir.resolve("app").toFile());
        configuration.afterPropertiesSet();
    }

    private File database(String name, Consumer<JdbcTemplate> setup) {
        File file = tempDir.resolve(name).toFile();
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite:" + file.getAbsolutePath(), true);
        dataSource.setDriverClassName("org.sqlite.JDBC");
        try {
            setup.accept(new JdbcTemplate(dataSource));
        } finally {
            dataSource.destroy();
        }
        return file;
    }

    private File lingollmDatabase(String name, Consumer<JdbcTemplate> afterMigration) {
        return database(name, jdbc -> {
            new Flyway(DatabaseCheck.flywayConfiguration(jdbc.getDataSource())).migrate();
            afterMigration.accept(jdbc);
        });
    }

    private static List<String> tables(File file) {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite:" + file.getAbsolutePath(), true);
        try {
            return new JdbcTemplate(dataSource).queryForList("SELECT name FROM sqlite_master WHERE type = 'table'", String.class);
        } finally {
            dataSource.destroy();
        }
    }

    private File stageRestore(File source) throws Exception {
        File pending = new File(configuration.getFolder(), DB + Configuration.PENDING_RESTORE_SUFFIX);
        Files.copy(source.toPath(), pending.toPath());
        return pending;
    }

    private List<String> backupFolder(String prefix) {
        return Arrays.stream(configuration.getDatabaseBackupFolder().list()).filter(n -> n.startsWith(prefix)).toList();
    }

    @Test
    void acceptsMigratedDatabase() {
        File db = lingollmDatabase("ok.sqlite", _ -> {
        });
        assertThatCode(() -> DatabaseCheck.check(db)).doesNotThrowAnyException();
    }

    @Test
    void acceptsBackupOfWalDatabase() {
        File copy = tempDir.resolve("copy.sqlite").toFile();
        lingollmDatabase("wal.sqlite", jdbc -> {
            jdbc.execute("PRAGMA journal_mode=WAL");
            jdbc.execute("VACUUM INTO '" + copy.getAbsolutePath() + "'");
        });
        assertThatCode(() -> DatabaseCheck.check(copy)).doesNotThrowAnyException();
    }

    @Test
    void rejectsFileThatIsNotSqlite() throws Exception {
        File file = tempDir.resolve("text.sqlite").toFile();
        Files.writeString(file.toPath(), "definitely not a database, but long enough", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> DatabaseCheck.check(file))
                .isInstanceOf(DatabaseCheck.InvalidDatabaseException.class)
                .hasMessageContaining("not a SQLite database");
    }

    @Test
    void rejectsOtherSqliteDatabase() {
        File db = database("other.sqlite", jdbc -> jdbc.execute("CREATE TABLE notes (id INTEGER PRIMARY KEY, text TEXT)"));
        assertThatThrownBy(() -> DatabaseCheck.check(db))
                .isInstanceOf(DatabaseCheck.InvalidDatabaseException.class)
                .hasMessageContaining("not a LingoLLM database");
    }

    @Test
    void rejectsDatabaseOfNewerVersion() {
        File db = lingollmDatabase("newer.sqlite", jdbc -> jdbc.update(
                """
                        INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, \
                        installed_by, execution_time, success) VALUES (999, '999', 'future', 'SQL', 'V999__future.sql', 0, '', 0, 1)"""));
        assertThatThrownBy(() -> DatabaseCheck.check(db))
                .isInstanceOf(DatabaseCheck.InvalidDatabaseException.class)
                .hasMessageContaining("newer version");
    }

    @Test
    void rejectsDatabaseWithFailedMigration() {
        File db = lingollmDatabase("failed.sqlite", jdbc -> jdbc.update(
                "UPDATE flyway_schema_history SET success = 0 WHERE installed_rank = (SELECT max(installed_rank) FROM flyway_schema_history)"));
        assertThatThrownBy(() -> DatabaseCheck.check(db))
                .isInstanceOf(DatabaseCheck.InvalidDatabaseException.class)
                .hasMessageContaining("failed migration");
    }

    @Test
    void restoresValidDatabaseOnStart() throws Exception {
        lingollmDatabase("current.sqlite", jdbc -> jdbc.execute("CREATE TABLE current_marker (id INTEGER)"));
        Files.copy(tempDir.resolve("current.sqlite"), configuration.getFolder().toPath().resolve(DB));
        File pending = stageRestore(lingollmDatabase("backup.sqlite", jdbc -> jdbc.execute("CREATE TABLE backup_marker (id INTEGER)")));

        configuration.resolveDb(DB);

        assertThat(pending).doesNotExist();
        assertThat(tables(configuration.getDatabaseFile())).contains("backup_marker").doesNotContain("current_marker");
        assertThat(backupFolder(Configuration.PRE_RESTORE_PREFIX)).hasSize(1);
        assertThat(backupFolder(Configuration.REJECTED_RESTORE_PREFIX)).isEmpty();
    }

    @Test
    void rejectsForeignDatabaseOnStartAndKeepsCurrent() throws Exception {
        lingollmDatabase("current.sqlite", jdbc -> jdbc.execute("CREATE TABLE current_marker (id INTEGER)"));
        Files.copy(tempDir.resolve("current.sqlite"), configuration.getFolder().toPath().resolve(DB));
        File pending = stageRestore(database("other.sqlite", jdbc -> jdbc.execute("CREATE TABLE notes (id INTEGER)")));

        configuration.resolveDb(DB);

        assertThat(pending).doesNotExist();
        assertThat(tables(configuration.getDatabaseFile())).contains("current_marker");
        assertThat(backupFolder(Configuration.REJECTED_RESTORE_PREFIX)).hasSize(1);
        assertThat(backupFolder(Configuration.PRE_RESTORE_PREFIX)).isEmpty();
    }
}
