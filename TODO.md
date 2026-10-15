# TODO

Planned work for LingoLLM. Ideas that need a decision first are marked with a question mark.

## Learning

- [ ] Evaluation of the level test: *Take Grade Test* of `GradeUIComponent` generates the test and the `TestDialog`
      records the answers (`TestResult` / `TestQuestionResult`, `finished` is set on Finish), but `onFinish` only says the judging is not
      available. It needs the judging of them (server judging for the choices and word order, the
      model for `WritingQuestionHandler`) and the evaluation that sets `Language.userGrade`.
- [ ] Free chat screen in the `Learning` tab (the inference layer, `stream` and function calls, is ready): a list of the chats of the selected profile
- [ ] Prompts: `FreeChat.rootPrompt` and `prompt` are stored but unused; decide on templates (Handlebars as in
      Marginalia?) and fill `LanguageDefaults.freeChatTemplate` for Japanese and German.
- [ ] Language specific screens (`ui/lang/languages/*`): exercises, vocabulary, progress in
      `Language.languageSpecificData`.
- [ ] More languages (`Languages` enum, `LanguageIcons`, `ENUM_LANGUAGE_*`).
- [ ] Editing and deleting a language profile (only creation exists), choosing the model of a profile?

## Platform

- [ ] Port `OsgiServiceImplTest` of Marginalia (extension loading on a real OSGi framework) and a test of
      `LanguagePart` / `LanguageCreationDialog` that builds the components without a UI session.
- [ ] Add the Flyway test with two migrations (older database copied before an upgrade, a restore whose migration
      fails) when `V2` exists; they were dropped from the port because `V1` was the only migration.
- [ ] Screenshots for the manual (`manual/images/`) once the screens are there.
- [x] Plugins: a first bundled extension as the example (`lingollm/plugins/gemma4toolsfix`).
- [ ] Interface languages other than English (extensions or bundles); the English bundle is a stub with the keys the
      code uses.
- [ ] Find out whether the dead-code leftovers from Marginalia remain (`Fulltextable`, `ImageAttachment` helpers, the
      `GenerateFlywayDiff` tool targets, `JavascriptLibrary` scroll helpers).

## Release

- [ ] Choose the real repository and URLs (`retype.yml`, README, workflows still point to `Enerccio/LingoLLM`).
- [ ] Decide whether `V1__initial.sql` is frozen from the first release (after it, every change is a new migration).
