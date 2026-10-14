---
label: Database & migrations
order: 950
covers:
  - lingollm/src/main/resources/migration
  - lingollm/src/main/java/com/github/enerccio/lingollm/Configuration.java
  - lingollm/src/main/java/com/github/enerccio/lingollm/DatabaseCheck.java
  - lingollm/src/main/java/com/github/enerccio/lingollm/SaneSQLiteDialect.java
  - lingollm/src/main/java/com/github/enerccio/lingollm/domain/service/impl/DatabaseBackupServiceImpl.java
  - lingollm/src/main/resources/META-INF/spring/datasources-config.xml
  - lingollm/src/main/webapp/config/persistence.xml
  - lingollm/src/main/java/com/github/enerccio/tools
  - lingollm/src/test/java/com/github/enerccio/lingollm/db
---

# Database & migrations

LingoLLM keeps all its data in one SQLite file. The schema is created and upgraded by Flyway migrations, and
Hibernate maps the entities onto it but never changes it. This page explains the setup, the conventions of the schema
and how to write a migration. What the tables contain is described in [Domain model](domain-model.md).

## The database file

| | |
|---|---|
| File | `<user.home>/.lingollm/lingollm.sqlite`, with `lingollm.sqlite-wal` and `-shm` while it runs |
| JDBC URL | `jdbc:sqlite:<file>?busy_timeout=10000&journal_mode=WAL` (built by `Configuration.resolveDb`) |
| Driver | `org.sqlite.JDBC` (`sqlite-jdbc`) |
| Pool | commons-dbcp `BasicDataSource`, 10-20 connections, validated with `SELECT 1` |
| Journal | WAL - readers don't block the writer; writers wait up to 10 s (`busy_timeout`) for each other |
| Foreign keys | Not enforced (`PRAGMA foreign_keys` is off), see [Conventions](#schema-conventions) |

The beans are in `src/main/resources/META-INF/spring/datasources-config.xml`:

```mermaid
flowchart LR
    conf["configuration<br/>resolveDb()"] --> ds["dataSource<br/>BasicDataSource"]
    ds --> fw["flyway<br/>init-method=migrate"]
    ds --> proxy["ds<br/>TransactionAwareDataSourceProxy"]
    fw -. "depends-on" .-> emf
    proxy --> emf["emf<br/>EntityManagerFactory (PU)"]
    proxy --> jdbc["jdbcTemplate"]
    emf --> em["em<br/>SharedEntityManagerBean"]
    emf --> tm["transactionManager<br/>JpaTransactionManager 'common'"]
```

- `flyway` migrates the database when the context starts; `emf` depends on it, so Hibernate only sees a migrated
  schema.
- Hibernate and `jdbcTemplate` use the data source through `TransactionAwareDataSourceProxy`, so plain JDBC code
  joins the current JPA transaction.
- The persistence unit `PU` is defined in `src/main/webapp/config/persistence.xml`: the list of entity classes
  (`exclude-unlisted-classes`), the dialect and `hibernate.hbm2ddl.auto=validate`.

### Looking into the database

The file is an ordinary SQLite database. While LingoLLM runs, open it read-only so you don't hold locks:

```sh
sqlite3 -readonly ~/.lingollm/lingollm.sqlite
sqlite> .tables
sqlite> select version, description, success from flyway_schema_history;
sqlite> select id, nameOverride, cast(extendedContent as text) from languages;
```

`extendedContent` columns hold UTF-8 JSON, so `cast(... as text)` (or `json_extract`) shows the
[extended attributes](domain-model.md#extendableentity---extended-attributes). Timestamps (`creation`,
`modification`, `lastOpened`) are stored as milliseconds since the epoch, booleans as `0` / `1`.

## Startup: migrate, then validate

1. Before the data source is created, `Configuration.resolveDb` swaps in a staged database restore, if there is one
   (see [Database backups and restores](#database-backups-and-restores)). Then `Configuration.backupBeforeMigration`
   asks `DatabaseCheck.pendingMigrationFrom` whether the existing file is behind the newest bundled `V<n>` migration
   (or has no history table, which Flyway baselines at V1). If so it saves a `VACUUM INTO` copy as
   `db-backups/pre-migration-<time>-V<from>-lingollm.sqlite` and keeps the newest three. A new installation (no file
   or an empty one) and a current database are left alone, and a copy that fails is logged, not fatal. The check
   looks at the Flyway history only, so a Java `Migration` bean without a `V<n>` file does not trigger a copy.
2. Flyway runs every pending migration from `classpath:migration` (`src/main/resources/migration/`) and records it in
   `flyway_schema_history`. `baselineOnMigrate=true` with baseline version `1` makes databases created before Flyway
   was introduced (V1 schema without a history table) start at V1 and get V2 onwards.
3. Hibernate builds the `EntityManagerFactory` and **validates** the schema against the entities: every mapped table
   and column must exist with a compatible type. It never creates or alters anything.

If either step fails, the Spring context doesn't start and LingoLLM doesn't serve any page; the cause is in the
log. Typical messages:

| Message | Cause |
|---|---|
| `Schema-validation: missing column [x] in table [y]` | An entity field was mapped as a column without a migration. |
| `Schema-validation: missing table [x]` | A new entity without a migration, or the migration names the table differently. |
| `Validate failed: Migrations have failed validation ... checksum mismatch` | An already applied migration file was edited. |
| `Migration V<n>__... failed` | The SQL of a migration failed; Flyway rolls that migration back (SQLite DDL is transactional, each file is its own transaction, earlier files of the same run stay applied) and startup stops. Flyway Community has no undo, so a migration that succeeded is only reversible from the `pre-migration-*` copy. |

There is also a second, Java-level version check: `ApplicationInitializer` compares `AppSettings.dbVersion` /
`appVersion` with the versions set in `container-config.xml` and runs registered `Migration` beans for data changes
that are easier in Java than in SQL. It runs after Hibernate is up, so a migration can use `jdbcTemplate` or services.
Each migration gets the current version and returns the new one (or the same one when it has nothing to do), so a
migration is written for one version step and ignores the others.

No data migration exists yet (the list in `applicationInitializer` is empty and `appVersion` is 1).

To add one: implement `Migration`, register the bean in the `migrations` list of `applicationInitializer` and raise
`appVersion` (or `dbVersion`). A fresh database starts at version 1, so it goes through all migrations too. Marginalia has examples (an encryption of plain text API keys, the refill of `_fulltext`) in its `bound/migration` package.

## Migrations

| Version | File | Change |
|---|---|---|
| V1 | `V1__initial.sql` | The initial schema: `users`, `settings`, `ais` (+ `ais_openaicompat`), `usages`, `resources`, `languages`, `free_chats` and `free_chat_messages`, each with its `<table>_SEQ` id table and the indexes of the lookups. |

LingoLLM has no released database yet, so until the first release `V1__initial.sql` may be edited when the model
changes (after editing it, `rm -rf lingollm/target/classes/migration` - stale scripts stay in `target/classes` - and
delete your development database). After the first release, every change is a new `V<n>` file, see the rules below.

### Rules

- **Never change a migration that has been released.** Flyway stores a checksum of each applied file and refuses to
  start when it changes. Fix mistakes with a new migration.
- **Name** files `V<next number>__<what_it_does>.sql` (two underscores). Versions are plain integers here.
- **One migration per change**, together with the entity change in the same commit.
- **Keep data.** Users upgrade in place - every migration must turn any existing database into the new schema
  without losing data. Convert old values in SQL.
- **Only SQL that SQLite understands.** See [SQLite limitations](#sqlite-limitations).
- **Prefer extended attributes.** A field stored in `extendedContent` needs no migration at all; add a column only
  when the value is used in queries (see [Domain model](domain-model.md#adding-a-field-or-an-entity)).

### Adding a column

```sql
-- V2__language_archived.sql
ALTER TABLE languages
    ADD COLUMN archived boolean not null default false;
```

`ADD COLUMN` with `not null` needs a `default`, otherwise existing rows can't get a value. Then map it in the entity:

```java
@Column(nullable = false)
private boolean archived = false;
```

### Adding a table

A new entity needs its table, its sequence table with a first row, and its indexes. Hibernate uses
`GenerationType.AUTO`, which on SQLite means a table-backed sequence named `<table>_SEQ` with a pooled optimizer
(ids are reserved in blocks of 50 - gaps in ids are normal):

```sql
-- V2__notebooks.sql
create table notebooks
(
    id              bigint      not null primary key,
    creation        timestamp,
    is_deleted      boolean     not null,
    modification    timestamp,
    uuid            varchar(36) not null unique,
    extendedContent blob,
    _fulltext       clob,
    userId          bigint,
    language_id     bigint
);

create index ix_notebooks_user on notebooks (userId, is_deleted);
create index ix_notebooks_language on notebooks (language_id);

create table notebooks_SEQ
(
    next_val bigint
);

INSERT INTO notebooks_SEQ (next_val) VALUES (1);
```

The columns of `BaseEntity` / `OwnedEntity` / `ExtendableEntity` are always the same: `id`, `creation`,
`is_deleted`, `modification`, `uuid`, `userId` (owner), `extendedContent` and `_fulltext`. Then list the class in
`persistence.xml`. For joined inheritance (a subtype of `AI`), the subtype table has only `id` plus its
own columns.

### Changing or removing a column

SQLite can rename a column (`ALTER TABLE ... RENAME COLUMN`) and, since 3.35, drop a simple one
(`ALTER TABLE ... DROP COLUMN`), but it can't change a column's type, nullability, default or constraints. For those,
rebuild the table:

```sql
CREATE TABLE languages_new ( ... new definition ... );

INSERT INTO languages_new (id, ...)
SELECT id, ... FROM languages;          -- convert values here

DROP TABLE languages;

ALTER TABLE languages_new RENAME TO languages;

CREATE INDEX ix_languages_user ON languages (userId, is_deleted);   -- indexes are dropped with the old table
```

Recreate every index of the old table. Removing an entity field without removing the column is harmless - validation
only checks that mapped columns exist.

### Generating the SQL

`com.github.enerccio.tools.GenerateFlywayDiff` (in `src/main/java/com/github/enerccio/tools/`) asks Hibernate what it
would change to make an existing database match the entities, and prints the SQL. Run its `main` from the
repository root (it reads `lingollm/src/main/webapp/config/persistence.xml`), with `-Duser.home` pointing to a
development home whose database is at the current schema version. Treat the output as a draft:

- Hibernate only creates and adds; it never drops or changes columns.
- It doesn't know about data conversion, defaults for existing rows or the table rebuilds SQLite needs.
- It may print Hibernate's temporary tables (`HT_*`, `HTE_*`, see below) - leave them out.

### Testing a migration

Every test that boots the Spring context (`LingoLLMTestBase`) migrates a fresh database with the real Flyway
configuration and validates it against `persistence.xml`, so a wrong script or an entity without a column fails nearly
every test. `db/DatabaseRestoreCheckTest` and `db/PreMigrationBackupTest` cover the checks around restores and the copy
saved before an upgrade. When you add a second migration, add a test that builds a database at the previous version (a
`Flyway` with `setTarget`, see `PreMigrationBackupTest.databaseAt`), inserts data and migrates it, so the data
conversion is checked; the tests that need two versions (an older database being copied before an upgrade, a restore
whose migration fails) were dropped from the port because there was only `V1` to build them from.

## Schema conventions

**Names.** Tables are plural (`languages`, `free_chats`, `free_chat_messages`), set by `@Table(name = ...)`. Columns use the Java
field name (`nameOverride`, `maxContext`); relations end in `_id` (`language_id`, `parentChat_id`), except the owner,
which is `userId`, and the soft delete flag, `is_deleted`. Indexes exist only in the migrations, see
[Indexes](#indexes).

**No foreign key constraints.** The tables have no `REFERENCES` clauses (SQLite wouldn't enforce them: `PRAGMA foreign_keys` is off). Integrity is kept by the application: rows are soft
deleted, so references stay valid, and the admin *Cleanup* follows the
[cleanup references](domain-model.md#cleanup-references) to decide what may be purged. Code that hard deletes must
clear or move references itself (see `CleanupServiceImpl.moveChildrenOfMessages`). Because cleanup nulls references
before it purges, a column that holds one must be nullable in the table (`free_chat_messages.parentChat_id`, `usages.ai_id`).

**Enums.** `AI.aiType` is stored as an ordinal (`tinyint`), all other enums by name.
`SaneSQLiteDialect` turns off the `CHECK` constraints that Hibernate would otherwise generate for enum columns, so a
new enum value doesn't need a table rebuild.

**Large values.** Text that can be long (`name`, `uri`, `apiKey`...) is `@Lob` → `clob`; `extendedContent` is a
`blob` with JSON. SQLite doesn't enforce lengths, so `varchar(255)` is only documentation.

**Encrypted columns.** Secrets are stored encrypted, see [Encrypted columns](#encrypted-columns).

**Hibernate's temporary tables.** Marginalia's `V1__initial.sql` contains `HT_*` and `HTE_*` tables that Hibernate uses
for bulk HQL updates and deletes (and inserts) on entities with joined inheritance (`AI`). LingoLLM's V1 does not have
them, because no code runs such a statement on `AI` (the cleanup removes entities one by one, and the bulk updates
touch `FreeChatMessage`, `Usage` and `Language` only, which have no subclasses). If you add a bulk `UPDATE` or
`DELETE` of `AI`, let `GenerateFlywayDiff` print the tables and add them in a new migration.

## Indexes

Indexes are created only by Flyway migrations; the entities don't declare any (`@Table(indexes = ...)`), because
Hibernate only validates the schema and never creates them. Name them `ix_<table>_<what>`.

Index what the queries filter on, in the order of the `WHERE` clause, with the equality columns first and the
`ORDER BY` column last:

| Index | Used by |
|---|---|
| `<table> (userId, is_deleted)` | Listing and finding the owner's entities (`ais`, `languages`). |
| `free_chats (userId, language_id, is_deleted)` | The chats of a language profile. |
| `free_chat_messages (parentChat_id, creation)`, `free_chat_messages (parent_id)` | Messages of a chat in order, children of a message. |
| `ais (shared, is_deleted)` | The shared AIs every user sees next to their own. |
| `usages (ai_id, userId)` (unique), `usages (userId, is_deleted)` | The usage of an AI by a user (the row `UsageRepository.add` updates), the usages of a user. |
| `resources (userId, hash)` | Deduplication of uploaded files. |
| `resources (userId, is_deleted, creation)` | The Resources tab: the user's files, newest first. |
| `resources (clazz, objectId)` | Resources used by an object (the loose link). |
| `settings (key, userId)` | Loading a settings object. |

A single-column index on `is_deleted` alone helps only the *Cleanup* (few rows are deleted); don't add new ones.
Check a query with `EXPLAIN QUERY PLAN` in SQLite - `SCAN <table>` means a full table scan, `SEARCH ... USING INDEX`
means the index is used.

## Encrypted columns

`OpenAICompatible.apiKey` is mapped with `@Convert(converter = EncryptedStringConverter.class)`. The converter calls
`Configuration.encrypt` / `decrypt`:

- On the first start `Configuration` writes a random UUID to `<data folder>/secret.key`; the AES-256 key is the
  SHA-256 of that UUID. The file is outside the database, so a database backup alone doesn't reveal the secrets.
- A stored value is `enc:` + Base64 of a random 12-byte IV and the AES/GCM ciphertext. Values without the prefix are
  read as they are (plain values from before the migration); a value that can't be decrypted (a database from another
  installation, a different `secret.key`) is logged and read as `null` - the user enters the key again.
- The converter is a Spring bean: `emf` sets `hibernate.resource.beans.container` to a `SpringBeanContainer`, so
  Hibernate asks Spring for converters (and entity listeners) and `@Autowired` fields are injected. Use the same
  converter for any new secret column.
- Code outside JPA (`jdbcTemplate`, migrations) sees the encrypted value and must use `Configuration` itself, (a Java migration that encrypts existing values would do that).

## SQLite limitations

| Limitation | Consequence |
|---|---|
| One writer at a time | Writes are serialized; long transactions block other users' writes (up to `busy_timeout`, then `SQLITE_BUSY`). Keep transactions short and never call a model inside one - such code runs outside transactions (`@NoTx`). |
| `ALTER TABLE` is limited | Type, nullability and constraint changes need a table rebuild. |
| DDL in transactions | SQLite supports transactional DDL, so a failing migration is rolled back completely. |
| No `VACUUM` inside a transaction | `VACUUM INTO` for backups runs with `@NoTx`. |
| Dynamic typing | A column accepts any value; the entity mapping is what keeps types consistent. |

## Database backups and restores

`DatabaseBackupServiceImpl` backs up the live database with SQLite's `VACUUM INTO '<file>'`, which writes a
consistent, compacted copy without stopping the application. Backups go to `<data folder>/db-backups/` as
`lingollm-<time>.sqlite` (manual) or `lingollm-scheduled-<time>.sqlite`; scheduled ones are rotated (keep last N). `Configuration` writes two more kinds into the same folder, `pre-restore-*` (the database a restore replaced) and `pre-migration-*` (see [Startup](#startup-migrate-then-validate)); the service lists every `.sqlite` file there, and only `lingollm-scheduled-*` counts as scheduled. The methods the UI calls (create, list, download, upload, delete, restore, schedule) call `AdminGuard.requireAdmin()`; the scheduled run (`createScheduledBackup`) and the rotation need no logged-in user.

A database can't be replaced while it's open, so restoring is done in two steps:

```mermaid
sequenceDiagram
    participant A as Admin UI
    participant B as DatabaseBackupService
    participant C as Configuration (next start)
    A->>B: scheduleRestore(backup)
    B->>B: DatabaseCheck.check(backup)
    B->>B: copy backup → lingollm.sqlite.restore (tmp file + atomic move)
    Note over A,C: restart
    C->>C: DatabaseCheck.check(lingollm.sqlite.restore)
    C->>C: move lingollm.sqlite (+ -wal, -shm) → db-backups/pre-restore-<time>-lingollm.sqlite
    C->>C: move lingollm.sqlite.restore → lingollm.sqlite
    C->>C: Flyway migrates the restored database
    Note over C: datasource, Flyway bean (nothing left to migrate), Hibernate validates
```

`DatabaseCheck.check(File)` is run on *Upload Backup* (`importBackup`), on `scheduleRestore` and on start. It opens the
file read-only and refuses it (`InvalidDatabaseException`, an `IllegalArgumentException` with the reason) when:

- it doesn't start with the SQLite header,
- `PRAGMA integrity_check` reports problems,
- core tables (`users`, `settings`, `ais`, `languages`, `free_chats`,
  `free_chat_messages`) are missing - another application's database,
- `flyway_schema_history` has a failed migration or a version newer than the newest bundled `migration/V*__*.sql` -
  a database of a newer LingoLLM version. A database without the history table predates Flyway and is baselined at
  V1 as usual.

`Configuration.applyPendingRestore()` migrates the restored database itself, before the datasource is created, with
the same Flyway configuration as the `flyway` bean (`DatabaseCheck.flywayConfiguration(DataSource)`, used by
`datasources-config.xml` too). If the check fails, the staged file is moved to
`db-backups/rejected-restore-<time>-lingollm.sqlite` and the current database stays. If the migration fails, the
restored file is moved there as well and the `pre-restore-*` database is moved back. Either way the reason is logged
as an error and LingoLLM starts with the previous database. A backup from an older version is upgraded on start.

API keys in a backup are encrypted with the installation's `secret.key` (see [Encrypted columns](#encrypted-columns)).
A backup restored on another installation loses them unless `secret.key` is copied along.

## Tests and the database

Tests don't touch your data: `LingoLLMTestBase` starts the Spring configuration with the data folder in
`target/test-home/ctx-*`, so every test context gets a fresh database migrated by the real Flyway configuration. See
[Testing](testing.md).
