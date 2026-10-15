package com.github.enerccio.lingollm.db;

import com.github.enerccio.lingollm.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code -Dreinitialize-with-sql}: the application folder is wiped before anything is created in it, Flyway
 * migrates a new database and the script is run on it.
 */
class ReinitializeWithSqlTest {

    @TempDir
    Path tempDir;

    private Path app;
    private Configuration configuration;

    @BeforeEach
    void setUp() throws Exception {
        app = tempDir.resolve("app");
        Files.createDirectories(app.resolve("extensions"));
        Files.writeString(app.resolve("extensions").resolve("old.jar"), "old");
        Files.writeString(app.resolve("secret.key"), "old-secret");
        Files.writeString(app.resolve("lingollm.sqlite"), "not even a database");
        Files.createDirectories(app.resolve("desktop"));
        Files.writeString(app.resolve("desktop").resolve("launcher-file"), "launcher");
        configuration = new Configuration();
        configuration.setFolder(app.toFile());
    }

    private Path script(String sql) throws Exception {
        Path file = tempDir.resolve("init.sql");
        Files.writeString(file, sql);
        return file;
    }

    private JdbcTemplate jdbc(File db) {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite:" + db.getAbsolutePath(), true);
        dataSource.setDriverClassName("org.sqlite.JDBC");
        return new JdbcTemplate(dataSource);
    }

    @Test
    void wipesMigratesAndRunsTheScript() throws Exception {
        configuration.setReinitializeWithSql(script("""
                -- a comment; with a semicolon
                CREATE TABLE reinit_marker (name TEXT);
                INSERT INTO reinit_marker VALUES ('one');
                INSERT INTO reinit_marker VALUES ('two');
                """).toFile());

        configuration.afterPropertiesSet();
        assertThat(app.resolve("extensions")).doesNotExist();
        assertThat(Files.readString(app.resolve("desktop").resolve("launcher-file"))).isEqualTo("launcher");
        assertThat(app.resolve("secret.key")).hasContent("old-secret");

        configuration.resolveDb("lingollm.sqlite");

        JdbcTemplate jdbc = jdbc(configuration.getDatabaseFile());
        assertThat(jdbc.queryForList("SELECT name FROM reinit_marker ORDER BY name", String.class))
                .containsExactly("one", "two");
        // migrated by Flyway, so nothing is left for the application's own run
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success = 1", Integer.class))
                .isPositive();
        assertThat(jdbc.queryForList("SELECT name FROM sqlite_master WHERE name = 'languages'", String.class)).hasSize(1);
    }

    @Test
    void dumpWithItsOwnTransactionIsAccepted() throws Exception {
        configuration.setReinitializeWithSql(script("""
                PRAGMA foreign_keys=OFF;
                BEGIN TRANSACTION;
                CREATE TABLE reinit_marker (id INTEGER PRIMARY KEY, name TEXT);
                INSERT INTO reinit_marker(id,name) VALUES(1,unistr('a\\u000ab'));
                COMMIT;
                """).toFile());

        configuration.afterPropertiesSet();
        configuration.resolveDb("lingollm.sqlite");

        assertThat(jdbc(configuration.getDatabaseFile()).queryForObject("SELECT name FROM reinit_marker", String.class))
                .isEqualTo("a\nb");
    }

    @Test
    void scriptInsideTheWipedFolderIsStillRun() throws Exception {
        Path inside = app.resolve("init.sql");
        Files.writeString(inside, "CREATE TABLE reinit_marker (name TEXT);");
        configuration.setReinitializeWithSql(inside.toFile());

        configuration.afterPropertiesSet();
        configuration.resolveDb("lingollm.sqlite");

        assertThat(inside).doesNotExist();
        assertThat(jdbc(configuration.getDatabaseFile())
                .queryForList("SELECT name FROM sqlite_master WHERE name = 'reinit_marker'", String.class)).hasSize(1);
    }

    @Test
    void missingScriptDeletesNothing() {
        configuration.setReinitializeWithSql(tempDir.resolve("missing.sql").toFile());

        assertThatThrownBy(configuration::afterPropertiesSet).hasMessageContaining("nothing was deleted");
        assertThat(app.resolve("extensions").resolve("old.jar")).exists();
        assertThat(app.resolve("lingollm.sqlite")).hasContent("not even a database");
    }

    @Test
    void failingScriptStopsTheStartAndRollsBack() throws Exception {
        configuration.setReinitializeWithSql(script("""
                CREATE TABLE reinit_marker (name TEXT);
                INSERT INTO no_such_table VALUES (1);
                """).toFile());

        configuration.afterPropertiesSet();
        assertThatThrownBy(() -> configuration.resolveDb("lingollm.sqlite")).hasMessageContaining("failed");

        assertThat(jdbc(new File(app.toFile(), "lingollm.sqlite"))
                .queryForList("SELECT name FROM sqlite_master WHERE name = 'reinit_marker'", String.class)).isEmpty();
    }

    @Test
    void refusesToWipeTheHomeFolder() throws Exception {
        String home = System.getProperty("user.home");
        System.setProperty("user.home", app.toString());
        try {
            configuration.setReinitializeWithSql(script("SELECT 1;").toFile());
            assertThatThrownBy(configuration::afterPropertiesSet).hasMessageContaining("refusing to wipe");
            assertThat(app.resolve("extensions").resolve("old.jar")).exists();
        } finally {
            System.setProperty("user.home", home);
        }
    }

    @Test
    void withoutTheArgumentNothingIsWiped() throws Exception {
        configuration.afterPropertiesSet();
        assertThat(app.resolve("extensions").resolve("old.jar")).exists();
    }
}
