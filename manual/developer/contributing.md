---
label: Contributing
order: 870
covers:
  - CONTRIBUTING.md
  - CLAUDE.md
  - tools
---

# Contributing

Contributions are welcome: bug reports, fixes, new features, extensions, and corrections to this manual. This page
describes how to report a problem, how changes are made and what a pull request should contain.

LingoLLM is released under the [MIT License](https://github.com/Enerccio/LingoLLM/blob/master/LICENSE). By
contributing you agree that your contribution is released under the same license.

## Reporting bugs and ideas

Open an issue on [GitHub](https://github.com/Enerccio/LingoLLM/issues). For a bug, include:

- what you did, what happened and what you expected;
- the version (desktop app archive name, or the commit for a Docker server) and how you run it;
- the inference provider type and model, if the problem is about a provider (never your API key);
- the relevant part of the log - see [Where are the logs?](../user/troubleshooting.md#where-are-the-logs). Logs may
  contain chat text; remove what you don't want to share.

Security problems (access to other users' data, login, cookies) should not be reported in a public issue - contact the
maintainer privately through GitHub first.

### Planned work

[`TODO.md`](https://github.com/Enerccio/LingoLLM/blob/master/TODO.md) is the list of planned work for the first
release, ordered by priority. Items marked 🧩 are good candidates for an extension instead of a change to the core.

## Before you start

- For anything larger than a small fix, open an issue (or comment on an existing one) first and describe what you
  plan. Some features belong in an extension rather than in the core - see [Plugin development](plugins/index.md).
- Read [Architecture](architecture.md) and the page about the area you are changing.
- Set up the build and make sure `mvn test` passes on a clean checkout - see [Building from source](building.md).

## Workflow

1. Fork the repository and create a branch from `master` with a short descriptive name (`fix-profile-selector`,
   `german-exercises`).
2. Make the change, with tests where the area is testable (see [Testing](testing.md)).
3. Run `mvn test` in `lingollm/`. If you touched classes plugins use, build your plugins too.
4. Start LingoLLM and try the change in the browser - most of the application is UI, which the tests don't cover.
5. Update the manual if users or developers will notice the change (see [Documentation](#documentation)).
6. Push and open a pull request against `master`.

CI runs the tests and builds the desktop app on all platforms for every pull request that changes `lingollm/`
(see [Continuous integration](packaging.md#continuous-integration)). A pull request is merged when CI is green and
the change has been reviewed.

## Commits

Commit messages follow the style already in the history: one line, **lowercase**, starting with a verb in the
imperative, class and method names in backticks, related changes separated by a semicolon:

```
add `softDeleteForLanguage` to `LanguageBoundService`; delete the chats of a deleted profile
throw `IllegalArgumentException` for a chat of another user in `FreeChatMessageServiceImpl`
fix `selectLanguage` not clearing the session language; correct `lastLanguage` reference in `UserSetting`
```

Common verbs: `add`, `fix`, `update`, `remove`, `refactor`, `rename`, `migrate`, `handle`. Mention the issue number
when the commit fixes one (`... (#17)`).

Keep commits focused - one fix or one step of a feature per commit, not a mix of a feature, a reformat and an
unrelated fix. Don't commit generated or local files: `target/`, `src/main/frontend/generated/`,
`src/main/bundles/`, `flow-build-info.json` in `src/main/resources` and `.idea/` are ignored by Git for this reason.

## Pull requests

A good pull request:

- does one thing, and says in the description **what** changed and **why**, with the issue it fixes;
- for a UI change, includes a screenshot or a short description of what to click to see it;
- adds or updates tests where it can, and passes `mvn test`;
- adds a Flyway migration for every entity change (see [Database & migrations](database.md)) - never edit a
  migration that is already in `master`;
- updates the manual;
- doesn't change formatting of code it doesn't otherwise touch.

## Code style

There is no formatter configuration in the repository; match the code around your change. In short:

### Java

- Java 25. Modern language features (records, `switch` expressions, pattern matching, text blocks, `var` where the
  type is obvious) are welcome.
- 4 spaces, no tabs; opening braces on the same line; lines up to about 120 characters.
- Javadoc on classes and on public methods whose purpose isn't obvious from the name. Write *why* and the contract
  (what `null` means, which thread, which transaction), not a restatement of the signature.
- Logging through SLF4J (`private static final Logger log = LoggerFactory.getLogger(X.class)`), with `{}`
  placeholders. Chat content and prompts are logged at `DEBUG` only; API keys and passwords never. Plugins
  don't use `LoggerFactory`: they take the logger from `OsgiService.createLogger(bundle)` so their lines reach the
  extension log ([Logging](plugins/overview.md#logging)).
- Return `null` for "not found" where the existing service API does (`find`, `findForUser`), and keep that consistent
  within a class.

### Naming

- An inference provider is `AI`, a language profile is `Language` (the language of the interface is `loc.Language`, a
  learnable language template `Languages`). Use the user-facing words (*inference provider*, *language profile*) in UI
  text, the manual and log messages.
- Services are an interface in `domain/service` and an implementation `<Name>Impl` in `domain/service/impl`,
  registered in the Spring XML (see [Services](services.md#writing-a-new-service)).
- UI classes: a tab of the workspace is `<Name>Part`, the screens of a learnable language live in `ui/lang/languages`, a
  dialog is `<Name>Dialog`, a reusable component in `ui/components` or `ui/widgets`.

### Architecture rules

- **Transactions** live on services (`@CommonTx`, `@CommonTxReadOnly`), never on UI classes. Entities held by the UI
  are detached; reload them through the service before changing them.
- **The current user** comes from the session-scoped `User` bean; services check ownership (`findForUser`) - the UI
  must not be the only thing protecting other users' data.
- **UI text** is never hard-coded: add a key to `loc/L.java` and the text to `loc/LocalizationBundleEN.java` (and to
  the bundles of other languages, if any; a language without the key shows the English text), and
  use `loc.getValue(L.KEY)` (see [User interface](ui.md#text)).
- **Threads**: long work runs off the UI thread, and UI updates go through `ui.access(...)` (see
  [Background work and push](ui.md#background-work-and-push)).
- **Extension points**: methods that extensions decorate are `@Extendable`, and each of them declares its signature
  with `@PublicAPI` (`PublicAPISignatureTest` fails when a method has none or the annotation is out of date). Renaming
  or removing such a method, its parameters or its local variables breaks plugins - update the annotation, check the
  plugins you know of and mention the change in the pull request (see
  [The public API](plugins/extendable.md#the-public-api) and
  [Keeping hooks working](plugins/extendable.md#keeping-hooks-working)).
- **Schema**: every entity change comes with a migration; Hibernate only validates the schema.

### Tests

Use JUnit Jupiter and AssertJ, the [test bases](testing.md#test-bases) and the fake LLM server; no real network calls.
See the [checklist for a new test](testing.md#checklist-for-a-new-test).

## Documentation

The manual you are reading is in `manual/` (Markdown for [Retype](https://retype.com)); `docs/` is the generated site.

- Change the Markdown in `manual/`, then run `retype build` and commit `docs/` in the same pull request. Never edit
  files in `docs/` by hand.
- Preview with `retype start`.
- Write for the reader of that part: the [User guide](../user/index.md) talks about what users see and click (UI
  names in *italics*, no class names), the developer guide about code (paths relative to the main package, class
  names in backticks).
- Screenshots go to `manual/images/` (there are none yet). Diagrams are Mermaid code blocks, not images.
- A screenshot that shows a third-party picture needs a credit on the [Credits](../credits.md) page (author, source,
  license, changes), and must be shared under the license of the picture.
- Every developer page has `verified` (the commit it was last checked against the code; a page gets it with the first `--stamp`) and `covers` (the code it
  describes) in its front matter. `python3 tools/check_dev_docs.py` lists the pages whose covered code changed since
  then (`-v` names modified files, `--check` fails when any page is behind). After you review a page, mark it with
  `--stamp <page.md>` in a commit of its own. When a page starts describing other code, extend its `covers`.
- Bugs you find while writing documentation are reported as an issue (see [Reporting bugs and ideas](#reporting-bugs-and-ideas)).

## Extensions

New features that not every user needs - and anything experimental - are often better as an extension than as a
change to the core. An extension can be developed and released on its own, and doesn't need a pull request at all. If
you want it bundled with LingoLLM in `lingollm/plugins/`, open an issue first. See
[Plugin development](plugins/index.md) and the [example plugin](plugins/example.md).
