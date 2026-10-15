# Project structure

A tour of the repository: what each folder and package contains and where to look when you want to change
something. [Architecture](architecture.md) explains how the parts work together; this page is the map.

## Repository root

```
LingoLLM/
├── lingollm/              the application - a Maven project (see below)
├── manual/                  sources of this manual (Retype, Markdown)
├── docs/                    generated manual, served by GitHub Pages - don't edit by hand
├── tools/                   check_dev_docs.py (stale developer pages)
├── .github/workflows/
│   ├── desktop.yml          CI: tests, desktop builds for 5 platforms, release upload
│   └── server.yml           CI: WAR, Docker image (ghcr.io), release upload
├── pom.xml                  parent POM: the version of the application and of the plugins, lists them as modules
├── Dockerfile               two-stage build: Maven builder → Jetty 12 image
├── docker-compose.yml       the server setup (port 8080, ./data volume, JVM options)
├── rebuild.sh, rebuild.bat  update a Docker server: pull, rebuild the image, restart
├── retype.yml               manual configuration (input manual/, output docs/)
├── README.md, CLAUDE.md, CONTRIBUTING.md, CHANGELOG.MD, TODO.md, LICENSE
```

| Path | Notes |
|---|---|
| `data/` | Created by `docker compose`: the data folder of the Docker server. Ignored by Git. |
| `.gitattributes` | Keeps line endings of the desktop launch scripts and of `rebuild.sh` / `rebuild.bat` (`LF` for the shell scripts, `CRLF` for the batch files). |
| `.dockerignore` | Excludes `lingollm/target/`, `lingollm/node_modules/` and `.idea/` from the Docker build context. |

There is no `lingollm/plugins/` folder yet. A plugin is its own Maven project (see [Plugin development](plugins/index.md));
add it as a module of the root `pom.xml` when it lives in this repository.

## The application (`lingollm/`)

```
lingollm/
├── pom.xml                       dependencies, build plugins, the desktop profile
├── src/main/java/                application code
├── src/main/resources/           Spring XML, the Flyway migration, log4j, Vaadin build info
├── src/main/resources-release/   WAR-only resources: the release log4j.properties
├── src/main/resources-raw/       on the classpath and copied into WEB-INF/classes without filtering (`japanese/joyo.csv`)
├── src/main/webapp/config/       configuration.properties, persistence.xml
├── src/main/frontend/            CSS for the Vaadin frontend
├── src/desktop/                  desktop launcher and launch scripts (not part of the WAR)
└── src/test/                     tests
```

## Java packages

Application code lives in `com.github.enerccio.lingollm` (paths below are relative to
`src/main/java/com/github/enerccio/lingollm/`), plus a helper tool in `com.github.enerccio.tools`.

### Top level

| File | |
|---|---|
| `Configuration.java` | The `configuration` bean: data folder (`~/.lingollm`) and its subfolders, database URL, staged database restore, the copy saved before a migration, saved login settings. |
| `Constants.java` | Application constants: `DEAD_SESSION_CHECK_TIMEOUT` (seconds without a heartbeat before `SessionManager` closes a UI, default 120). |
| `UIConstants.java`, `CssStyles.java` | Column widths and dialog offsets; CSS class names defined in `shared-styles.css`. |
| `DatabaseCheck.java` | Checks a database file before it is used or restored (core tables, Flyway history, version). |
| `SaneSQLiteDialect.java` | Hibernate's community SQLite dialect without generated `CHECK` constraints. |

### `bound` - startup and sessions

| File | |
|---|---|
| `ApplicationInitializer.java`, `Migration.java` | Runs registered `Migration`s (data migrations in Java) when the stored `AppSettings` versions are behind. None are registered yet; schema migrations are Flyway SQL. |
| `SessionManager.java` | The `sessionManager` bean: registry of all HTTP sessions, binds the logged-in user to them, runs code in other users' sessions (`runForUsers`) and closes dead UIs and sessions on its watcher thread. See [Sessions](architecture.md#sessions). |
| `SessionTrackingListener.java` | `HttpSessionListener` registered by `WebappApplicationInitializer`; forwards session creation and destruction to `SessionManager`. |
| `SessionInformation.java`, `SessionCloseListener.java`, `RunInSession.java` | What `SessionManager` knows about one session; callbacks. |
| `ApplicationPoint.java` | The `applicationPoint` bean: application-wide maps of open UIs to their `Workspace` and to a copy of their logged-in user. |
| `SessionPoint.java` | Session-scoped bean; holds the `loc.Language` of the session's UI (set from the user's settings when the workspace opens, read by `LocalizationImpl`). |

### `ui` - user interface

| Package | Files |
|---|---|
| `ui.main` | `WebappApplicationInitializer` (creates the Spring context, registers the session listener), `LingoLLMServlet` (the `VaadinServlet`), `AppShellConfig` (push, theme, global CSS), `LoginCheckRoute` (login overlay, saved logins, first-start dialog), `Main` (route `/`), `CssRegistry` (CSS of extensions). |
| `ui.workspace` | `Workspace` (interface of the workspace shown after login), `DesktopWorkspace` (its tabbed implementation with footer buttons), `WorkspaceComponent` (interface of its tabs), `LanguageUI` / `LanguageUIBase` (what the workspace shows for a language profile: a tab sheet of `LanguageUIComponent`s such as `GradeUIComponent` and `FreeChatComponent`). |
| `ui.workspace.phone`, `ui.workspace.phone.parts` | `PhoneWorkspace` (an `AppLayout` with a drawer instead of the left-hand tabs), `PhoneAIPart`, `PhoneSettingsPart` (`UserPart` with a stacked header), `PhoneTrashPart` (trash with `PhoneTrashGrid`). |
| `ui.workspace.parts` | One class per workspace tab: `LanguagePart` (Learning: the language selector and the `LanguageUI`), `UserPart` (Settings), `AIPart` (Inference Providers), `ResourcesPart`, `TrashPart`, `AdminPart`. |
| `ui.workspace.parts.admin` | Admin panels with their own logic: `DatabaseBackupPanel`, `CleanupPanel`. |
| `ui.lang`, `ui.lang.languages` | The learnable languages: `LanguageDefaults` (what a new profile starts with), `LanguageIcons` (flags as SVG) and one `LanguageUI` per language (`Japanese`, `German`). |
| `ui.dialogs` | `LanguageCreationDialog` (a new language profile; the first-start flow), entity dialogs (`AIDialog`, `UserDialog`), the limits of a shared AI (`AILimitsDialog`, `AILimitEditDialog`; `AILimitInfoDialog` is the user's own view), `UsageDialog`, `ExtensionLogDialog` and generic dialogs: `ConfirmDialog`, `TextInputDialog`, `ListSelectDialog`, `ErrorDialog`, plus the threading helper `UIPushGuard`. |
| `ui.components` | `MessageImages` (images of attachments, read when the browser asks for them) and `ThreadCopyRequestAttributes` (carries the session to worker threads). |
| `ui.widgets` | `LanguageItem` (flag + name of a language or profile), `LanguageSelector` (language of the interface), `HTabSheet` (tab sheet with the tabs on the left and room for custom content), `BackendTableProvider*` / `BackendTableItem` (lazy grids backed by a repository query), `TrashGrid` / `PhoneTrashGrid`, `TextAreaPopoverComponent` / `TextFieldPopOverComponent`, `HtmlText`, `ScrollPanel`, `ResizableTextArea`, `Notification`, `PermissiveLoginOverlay` (allows empty passwords). |
| `ui` | `JavascriptLibrary` - every script the app runs in the browser. |

### `domain.model` - entities

| File | |
|---|---|
| `BaseEntity`, `OwnedEntity`, `ExtendableEntity` | Mapped superclasses: id, uuid, soft delete, timestamps; owner; JSON `attributes` for extensions. |
| `LanguageBoundEntity` | Mapped superclass of everything that belongs to a language profile (`language`). |
| `ImageAttachment` | Record of a loose attachment: resource uuid and caption. |
| `Setting` | Base of settings, single table `settings` with a `DTYPE` discriminator. |
| `impl/Language` | A language profile of a user. |
| `impl/FreeChat`, `impl/FreeChatMessage` | A free conversation and its messages (a tree through `parent`). |
| `impl/Test`, `impl/TestQuestionGroup`, `impl/TestQuestion`, `impl/TestResult`, `impl/TestQuestionResult` | A test, its questions, an attempt at it and the answers of the attempt. |
| `impl/AI`, `impl/OpenAICompatible` | Inference providers (`AI` is the base, `OpenAICompatible` the only type). |
| `impl/Resource` | Uploaded files stored by hash; `clazz` + `objectId` loosely note what uses it. Listed in the Resources tab. |
| `impl/Usage` | What a user used of an AI (tokens, requests, tokens since the last reset of their limit). |
| `impl/settings/AppSettings`, `impl/settings/UserSetting` | Installation-wide settings (versions, database backup schedule) and per-user settings. |

`User` is in `domain.security.model`. Every entity must also be listed in `src/main/webapp/config/persistence.xml`.
See [Domain model](domain-model.md).

### `domain.repository` - data access

One interface and one JPA implementation (`impl/Jpa*Repository`) per entity, built on `BaseRepository` →
`OwnedRepository` → `ExtendableRepository` (→ `LanguageBoundRepository` for language bound entities). The beans are
defined in `services-config.xml` with the shared `EntityManager`.

### `domain.components` - plain data and helpers

| Class | |
|---|---|
| `AILimit`, `AILimits`, `LimitStatus` | The limit of one user on a shared AI, the JSON in `AI.limits` that holds them by user uuid, and where a user stands. See [Limits](domain-model.md#limits-of-the-users-of-a-shared-ai). |
| `CronSchedule` | Cron expression parsing and next-run computation, used by the database backup schedule. |

### `domain.service` - business logic

Interfaces in `domain.service`, implementations in `domain.service.impl`:

| Service | Responsible for |
|---|---|
| `BaseService`, `OwnedService`, `ExtendableService`, `LanguageBoundService` | Generic CRUD, soft delete, lookups restricted to the current user (and to a language profile). |
| `TemplateService` | Renders and validates Handlebars prompt templates (``, nothing escaped, compiled templates cached); `isValidTemplate` can report names that are not among the known variables. Marginalia's macros are not part of it. |
| `LanguageService`, `FreeChatService`, `FreeChatMessageService` | Language profiles, free chats and their messages. |
| `LanguageDefinitionService` | What the application knows about one learnable language (`getLanguage()`, language string schema, `createGaugeTest`). `BaseLanguageDefinitionService` is the base, one implementation per language in `impl/languages` (`JapaneseLanguageDefinitionServiceImpl`, `GermanLanguageDefinitionServiceImpl`, beans `japaneseLanguageDefinitionService` / `germanLanguageDefinitionService`); the `Japanese` / `German` UI autowires its own by `@Qualifier`. |
| `TestQuestionFactoryRegistry`, `TestQuestionGroupFactoryRegistry` | The `TestQuestionFactory`s and `TestQuestionGroupFactory`s by class name (built-in ones are loaded and cached on first use, extensions register theirs). |
| `TestService`, `TestQuestionGroupService`, `TestQuestionService`, `TestResultService`, `TestQuestionResultService` | Tests, their questions, attempts and answers (deleting a test deletes the rest). |
| `AIService`, `SettingService`, `ResourceService` | Inference providers, settings, files. |
| `AILimitService` | The per-user limits of a shared AI: the owning administrator sets them, `check` refuses a request of a user who is over their limit, `getStatus` is for the user's own view. |
| `UsageService` | Tokens a user used with an AI (`Usage`, one per AI and user); `findAllUsages()` lists everybody's for administrators. |
| `InferenceServices`, `InferenceService`, `InferenceException`, `InferenceErrors` | Choosing and calling the model API (list models, test a connection); classified failures and their localized texts. |
| `DatabaseBackupService` | Database backups and their schedule. |
| `CleanupService`, `TrashService` | Purging soft-deleted data that is no longer referenced; listing, restoring and (messages) permanently deleting it (one bean, `CleanupServiceImpl`, shares the reference model). |
| `OsgiService`, `ExtensionService` | Loading, stopping and starting extensions (the `.STOPPED` flag), the logger of an extension and its log file; `@Extendable` decorators. |

`service.impl.inference.OpenAICompatibleInferenceService` is the openai-java client with the mapping of API failures to
`InferenceException`.

### `domain.security`

`model/User`, `repository/UserRepository` (+ `impl/JpaUserRepository`), `service/UserService` (+
`impl/UserServiceImpl`: authentication, password hashes, saved logins, protection of the last administrator),
`AdminGuard` (`requireAdmin()`, used by the administrator-only services) and `PersistedLoginInfo` (a saved login).

### `domain.traits`, `domain.collections`, `domain.listener`

| Package | |
|---|---|
| `domain.traits` | Annotations: `@CommonTx`, `@CommonTxReadOnly`, `@NoTx` (transactions), `@LLMFunction`, `@LLMParam`, `@LLMSchema`, `@LLMField` (what the model calls and answers with), `@Extendable` (instrumented UI classes) with `@PublicAPI`, `@APIReturn`, `@APIParameter`, `@APILocals`, `@ExtendedAttribute` (fields stored in the extended content), `@Fulltextable`, `@CleanupReference(s)` (how references behave during cleanup), `@SupportedAI` (provider type of an inference service). |
| `domain.collections` | Enums: `Languages` (the learnable languages), `AIType`, `ReasoningEffort`, `LimitStrategy`, `ResetStrategy`. |
| `domain.listener` | `ExtendableEntityListener` - serializes `attributes` and `@ExtendedAttribute` fields to `extendedContent` and back, and collects the `@Fulltextable` fields into `_fulltext`. |

### Extensions, localization, helpers

| Package | |
|---|---|
| `extensions` | `LingoLLMExtension` - the service interface every extension bundle registers; `ExtensionHasCss` and `ExtensionHasLocalization` - optional interfaces of an extension. |
| `instruct` | `RuntimeInstrumentationInitializer`, `ExtendableMethodVisitor`, `ExtensionServiceHolder`; `instruct.verify`: `ExtensionVerifier` and its helpers, which check an extension's decorators against the application before it is started. |
| `loc` | `L` (all text keys), `Localization` / `LocalizationImpl` (picks the bundle of the session's language), `Language` (a language of the interface), `LocalizationBundle` / `LocalizationBundleBase` / `LocalizationBundleEN`, `NaturalOrderComparator`. |
| `concurrent` | `AsyncRunnableWrapper`, `ThrowingRunnable`. |
| `utils` | `UIUtils`, `ClientAddressResolver`, `ReflectUtils`, `ThreadUtils`. |
| `com.github.enerccio.tools` | `Pair`, `Pointer` and `GenerateFlywayDiff` (development tool, see [Database schema changes](building.md#database-schema-changes)). |

## Resources and configuration

| Path | |
|---|---|
| `src/main/resources/META-INF/spring/application-config.xml` | Root of the Spring configuration; imports the three files below. |
| `.../spring/container-config.xml` | Localization, configuration, `applicationPoint`, `sessionManager`, the session beans `sessionPoint`, `user` and `language`, application initializer. |
| `.../spring/datasources-config.xml` | Data source, Flyway, JPA, transaction manager. |
| `.../spring/services-config.xml` | Repositories, services, extensions. |
| `src/main/resources/migration/V<n>__<name>.sql` | Flyway migrations - currently `V1__initial`. |
| `src/main/resources/log4j.properties` | Logging for development (IDE, `mvn jetty:run`) - `DEBUG`. |
| `src/main/resources-release/log4j.properties` | Logging packaged into the WAR instead - `INFO` (see [Packaging & releases](packaging.md#the-war)). |
| `src/main/webapp/config/configuration.properties` | Application settings (saved logins). |
| `src/main/webapp/config/persistence.xml` | The persistence unit: list of entity classes, dialect, `hbm2ddl.auto=validate`. |

## Frontend (`src/main/frontend/`)

There is no hand-written frontend application - Vaadin generates it. The only source is `styles/shared-styles.css`:
global CSS imported by `AppShellConfig` (spacing helpers, utility classes, Markdown wrapping, language flags).

Generated and ignored by Git: `src/main/frontend/generated/`, `src/main/frontend/index.html`, the development bundle
in `src/main/bundles/`, `node_modules/`, `package.json` and the Vite configuration.

## Desktop app (`src/desktop/`)

| Path | |
|---|---|
| `java/.../desktop/DesktopLauncher.java` | The launcher: options, Jetty base in `~/.lingollm/desktop`, starts Jetty in-process, browser, tray icon, single instance. JDK only. |
| `bin/lingollm`, `bin/lingollm.bat` | Start scripts of the portable folder (bundled runtime + JVM options + `LINGOLLM_JAVA_OPTS`). |

Built only by the `desktop` profile. See [Packaging & releases](packaging.md).

## Tests (`src/test/`)

Test classes mirror the areas they cover (paths relative to `src/test/java/com/github/enerccio/lingollm/`):

| Package | |
|---|---|
| `test` | Test infrastructure: `LingoLLMTestBase` (Spring context, temp data folder, login helpers), `ExpectedLog`, `TestContextPostProcessor`. |
| `test.llm` | `MockLLMServer` - a fake OpenAI-compatible server with programmable scenarios. |
| `crud` | CRUD tests for every entity, built on `OwnedCrudContract` / `ExtendableCrudContract`. |
| `db` | Database restore check and the copy before a migration. |
| `backup` | The database backup schedule. |
| `instruct` | Bytecode instrumentation (with fixture classes in `instruct.fixture`), the extension verifier and the check that every method of an `@Extendable` class declares its `@PublicAPI` (`PublicAPISignatureTest`). |
| `domain`, `domain.service` | Entity listener, AI limits and sharing, usage, cron expressions. |
| `loc`, `security`, `utils`, `ui.main` | Localization, helpers, CSS registry. |

`src/test/resources/META-INF/spring/test-application-config.xml` is the test variant of the Spring configuration.
See [Testing](testing.md).

## Build output (`lingollm/target/`)

| Path | |
|---|---|
| `lingollm-1.0.0.war`, `lingollm-1.0.0/` | The WAR and its exploded form. |
| `lingollm-1.0.0-classes.jar` | Classes for plugin builds. |
| `classes/`, `test-classes/` | Woven classes. |
| `surefire-reports/`, `test-home/` | Test reports and test data folders. |
| `desktop/` | Desktop distribution (with `-Pdesktop`). |

## Where to find...

| I want to change... | Look at |
|---|---|
| A text in the UI | `loc/L.java` (key) and the text in `loc/LocalizationBundleEN.java` |
| The language selector or the first-start flow | `ui/workspace/parts/LanguagePart.java`, `ui/dialogs/LanguageCreationDialog.java` |
| What a language shows | `ui/lang/languages/` (one class per language), `ui/workspace/LanguageUIBase.java` |
| A new learnable language | `domain/collections/Languages.java`, `ui/lang/`, a `LanguageDefinitionService` (class in `domain/service/impl/languages` extending `BaseLanguageDefinitionService`, bean in `services-config.xml`), `ENUM_LANGUAGE_*` keys, `LocalizationImpl.loadMaps` |
| An entity or the schema | the entity in `domain/model/impl/`, the Flyway migration, `persistence.xml` - see [Database & migrations](database.md) |
| A new service | interface in `domain/service/`, implementation in `impl/`, a bean in `services-config.xml` |
| What an extension can hook into | classes annotated `@Extendable` in `ui/` |
| Global styles | `src/main/frontend/styles/shared-styles.css` and `CssStyles.java` |
| Login and saved logins | `ui/main/LoginCheckRoute.java`, `UserServiceImpl` |
| Database backups | `DatabaseBackupServiceImpl`, `Configuration` (pending restore, copy before a migration) |
| The desktop app | `src/desktop/java/.../DesktopLauncher.java`, the `desktop` profile in `pom.xml` |
