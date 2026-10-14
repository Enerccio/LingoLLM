# Contributing to LingoLLM

Contributions are welcome: bug reports, fixes, features, extensions and corrections to the manual. LingoLLM is
released under the [MIT License](LICENSE); by contributing you agree that your contribution is released under it.

The full guide (workflow, commit style, code style, architecture rules, documentation) is
[manual/developer/contributing.md](manual/developer/contributing.md). This page is the short version, with the pitfalls
that most often trip up new contributors.

## Reporting problems and ideas

Open a GitHub issue. For a bug, say what you did, what happened, what you expected, which version you run (desktop
archive name or commit) and, for provider problems, the provider type and model. **Never include API keys**, and
remove chat text from logs you don't want to share.

Security problems (access to other users' data, login, cookies, extensions) should not go in a public issue: contact
the maintainer privately through GitHub first.

For anything larger than a small fix, open or comment on an issue before writing code. Features that not every user
needs are often better as an [extension](manual/developer/plugins/overview.md), which needs no pull request at all.
Planned work is in [`TODO.md`](TODO.md).

## Setting up

You need **JDK 25** and **Maven 3.9**, and you must build from a **git clone** (the build reads git information).
Everything is run from the `lingollm/` directory:

```sh
mvn package                 # build + tests
mvn package -DskipTests
mvn test -Dtest=LanguageCrudTest
mvn install -DskipTests     # needed before building plugins (and `mvn -N install` once in the repository root)
```

The first build is slow: Vaadin downloads Node.js and npm packages. See
[Building from source](manual/developer/building.md) for running locally, IntelliJ setup and Docker.

Use a separate user home for development runs so you don't touch your real data (`~/.lingollm`):

```sh
export MAVEN_OPTS="-XX:+EnableDynamicAgentLoading -Djdk.attach.allowAttachSelf=true -Duser.home=/path/to/dev-home"
mvn -DskipTests jetty:run -Dmaven.repo.local=$HOME/.m2/repository
```

## Pitfalls to know before you start

- **Build with AspectJ (`ajc`), not plain javac.** UI classes are created with `new` and get `@Autowired` fields through
  compile-time weaving. An IDE that compiles with plain javac builds fine, but every injected field is `null` at
  runtime. Set IntelliJ to the Ajc compiler (see the building guide). Keep the `-parameters` and `preserveAllLocals`
  compiler options: extensions read method arguments and locals by name.
- **A new service or repository needs a bean in `services-config.xml`.** Component scan doesn't find service
  implementations.
- **A new entity needs three things:** an entry in `webapp/config/persistence.xml`, a Flyway migration
  (`src/main/resources/migration/V<n>__*.sql`), and, if it references other data, a `@CleanupReference`. Hibernate only
  validates the schema, so an entity change without a migration breaks startup. Never edit a migration that is already
  in `master`.
- **Ownership:** every entity belongs to a user, and what a user learns belongs to one of their language profiles
  (`LanguageBoundServiceImpl.save` takes it from the session's `language` bean). Use `findAllForUser()` / `findForUser(uuid)`; never trust a raw id from
  the UI. Deletes are soft (`deleted = true`).
- **Transactions** come from Spring proxies (`@CommonTx`, `@CommonTxReadOnly`) on services only, so a self-call inside a
  service is not transactional. UI entities are detached; reload them through the service before changing them.
- **Threads:** long work runs on a worker thread. UI updates from other threads go through `ui.access(...)`.
- **`@Extendable` methods are a public API.** Renaming or removing such a method, its parameters or its local variables
  breaks plugins. Check the plugins you know of and mention it in the pull request.
- **UI text is never hard-coded.** Add a key to `loc/L.java` and the English text to `loc/LocalizationBundleEN.java`. Both
  files are grouped by key prefix (`LABEL_`, `ENUM_`, `ERROR_`, `MSG_`, `HELP_`, `DESC_`): add the key at the end of its
  prefix group in both files, not at the end of the file. Keys name the English text, not the screen, so reuse an
  existing key (e.g. `LABEL_REFRESH`) instead of adding a per-screen duplicate; grep `LocalizationBundleEN` first. Remove keys nothing uses.
- **Don't log chat content** above `DEBUG`, and never log API keys or passwords.
- **Plugins** are separate Maven projects: run `mvn -N install` in the repository root (the parent POM that holds the version)
  and `mvn install -DskipTests` for the app first, then `mvn package` in the plugin directory. `mvn package` in the
  root builds the app and all plugins in one go. They must be built from the same source tree as the app.

## Making a change

1. Fork, and branch from `master` with a short name (`fix-profile-selector`).
2. Make the change, with tests where the area is testable. Tests boot the real Spring XML on SQLite and use a fake
   OpenAI-compatible server, so they need no network or API key (see [Testing](manual/developer/testing.md)).
3. Run `mvn test` in `lingollm/`. If you touched classes plugins use, build your plugins too.
4. Run the app and try the change in the browser; the tests don't cover most of the UI.
5. Update the manual if users or developers will notice the change.
6. Open a pull request against `master`.

**Commit messages:** one line, lowercase, imperative verb first, code names in backticks, related changes separated by a
semicolon, for example ``fix `selectLanguage` not clearing the session language``.
Keep commits focused, and don't commit generated or local files (`target/`, `src/main/frontend/generated/`,
`src/main/bundles/`, `.idea/`, `.DS_Store`).

**A good pull request** does one thing, says what changed and why, includes a screenshot for UI changes, adds a
migration for every entity change, updates the manual, and doesn't reformat code it doesn't otherwise touch.

## Documentation

The manual lives in `manual/` (Markdown, built with [Retype](https://retype.com) into `docs/`): `manual/user` for users,
`manual/developer` for contributors. Write user pages in terms of what people see and click (UI names in *italics*, no class names), and
developer pages in terms of code. Put screenshots in `manual/images/`. See
[Documentation](manual/developer/contributing.md#documentation) in the full guide for the build and publishing steps.
