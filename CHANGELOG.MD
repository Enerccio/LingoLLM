# Changelog

All notable changes to LingoLLM are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

First development version. The platform is taken from [Marginalia](https://github.com/Enerccio/Marginalia) (without
anything specific to its stories); the database starts at `V1`.

### Added

- `-Dlingollm-autologin=<login>` (desktop app: `--lingollm-autologin <login>`): that user is logged in without the login
  screen, but only when they have no password (otherwise, or when the user is missing or locked, the login screen is
  shown). New `Configuration.getAutologin`, `UserService.canAutologin` and `LoginCheckRoute.autologinUser` (a new
  `@Extendable` method).
- The grade test can be taken: **Take Grade Test** (tab *Level*) has the model write the test (the default model of the
  user, the only one they have, or one chosen from a list) while the new modal `TestGenerationDialog` shows a progress bar,
  how many questions are written and how many are left, and a **Stop Generation** button, then opens the test in the
  new `TestDialog` (a window on a desktop, the whole screen on a phone; one question at a time,
  *Previous*/*Next*/*Finish*, an **I don't know** check box under every question). The answers are only collected for
  now, judging them is not there yet.
- `TestQuestionHandler.render()` (the component of a question for the test dialog) and `getAnswer()` for every kind of
  question (an index, a JSON list of indexes, a text), `TestQuestionGroupHandler.render()` for what a group shows above
  its questions. Both are new abstract/final methods: question handlers of extensions have to implement them.
- `LanguageStringComponent`: shows a `LanguageString` with its annotations (furigana as `<ruby>`), can hide annotations
  with an `AnnotationFilter` (`Furigana.hideOver(kanji)`) and takes `Annotator`s for other kinds of metadata.
- `TestQuestionResult.dontKnow` (extended attribute, `false` by default): the learner said they do not know the answer.
- Japanese level test: 10 more questions, `KanjiReadingFactory` / `KanjiReadingHandler` (a single choice question: the
  reading of the `targetKanji` of a `sentence`; the model writes the furigana of the whole sentence as always and the
  question hides the ones over the target), their levels spread over A1-C2 with the kanji of the JLPT level that is
  about the same (N5 for A1 ... N1 for C1 and C2).

- `-Dreinitialize-with-sql=/path/to/file.sql` (desktop app: `--reinitialize-with-sql <file>`): before the database exists
  `Configuration` wipes the content of the application folder (not `secret.key` and not the launcher's `desktop/` folder), Flyway creates and
  migrates the database, the SQL file is run on it in one transaction, and the application continues. Nothing is deleted
  when the file is not readable. `BEGIN`/`COMMIT` lines of the file (a `sqlite3` dump) are ignored. `Configuration.resolveDb` now declares `throws Exception`.
- Tests are written by the model in phases: `LanguageDefinitionService.createTest(language, ai, name, requests, callback)`
  (any test) and `createGaugeTest(language, ai, callback)` (the level gauging test) are asynchronous, return a
  `CancellationToken` and ask the model for the questions three at a time (`BaseLanguageDefinitionService.BATCH_SIZE`),
  each request telling it what was written already; a batch that is not what was asked (wrong kinds or order, an invalid
  question, no furigana in Japanese...) is asked again with the problems, up to 3 times, and the accepted batches are
  stitched into one `Test` in one group, with its `TestQuestion`s, saved only when all of them are valid. The callback
  (`LanguageDefinitionService.TestCallback`, was `GaugeTestCallback`) also gets `onProgress(done, total)`.
  Every `TestQuestionRequest` has the CEFR `level` of the question; the level gauging test is 21 questions (6 about a text,
  15 others: single and multiple choice, writing and word order), the levels of each section spread evenly over A1-C2
  (`spreadLevels`). Languages can add questions between the sections and at the end (`questionsBetweenSections`,
  `questionsAtEnd`, `questionKinds` in `BaseLanguageDefinitionService`). The reasoning of the model is kept in
  `Test.reasoning`, per batch; for that `InferenceService.InferenceOutcomeCallback` has a new `onReasoning`.
- New kinds of questions: `WritingQuestionFactory` (the learner writes an answer, the model gives a sample one) and
  `WordOrderQuestionFactory` (the model gives the segments of a sentence in the correct order, the code shuffles them).
- Prompts are Handlebars templates (`{{variable}}`), rendered by `TemplateService` as in Marginalia but without its
  SillyTavern macros (`processTemplate`, `isValidTemplate` with a list of names to report unknown ones, compiled
  templates cached by Caffeine; new dependencies `com.github.jknack:handlebars`, `com.github.ben-manes.caffeine:caffeine`).
  `LanguageDefaults` holds the create test prompt of each language (`getCreateTestPrompt`), one generic prompt that writes
  the questions asked for whatever the test is for; `Language.defaults` is now a `LanguageDefaults` with what the user
  changed, and `LanguageService.getCreateTestPrompt` takes the profile's own text when it is set and the default of the
  language otherwise.
- `LanguageDefinitionService` (what the application knows about a learnable language) with `BaseLanguageDefinitionService`
  and one implementation per language (`JapaneseLanguageDefinitionServiceImpl`, `GermanLanguageDefinitionServiceImpl`).
- `TestQuestionHandler.getLlmSchema()` (JSON schema of what the model writes for a question, extensions that add
  question types can override it), `getLlmTypeName()` and `TestDefinitionSchemaGenerator`, which builds the schema of a
  whole test from a list of handlers. The data classes of the questions describe their fields with `@LLMField`.
- Language profiles have the level the user says they have: `Language.userGrade` (`CEFR`, A1-C2, each with a short
  description for the model) and `Language.userGradeFineDetails` (free text). The levels have localized names
  (`ENUM_CEFR_*`, `Localization.getCefrLevel`).
- The *Learning* tab of a language profile is a tab sheet of `LanguageUIComponent`s built by `LanguageUIBase` (also on a
  phone): **Level** (`GradeUIComponent`: the CEFR level, the user's impressions, a *Take Grade Test* button and a save
  button; the button only says the test is not available yet) and **Free chat** (`FreeChatComponent`, empty for now). A
  profile without a level has the other tabs frozen until the level is saved. A language adds its own tabs by
  overriding `LanguageUIBase.createComponents()`. `LanguageUIComponent` has a new `refresh()`.
- Tests: language bound entities `Test`, `TestQuestion`, `TestResult` (an attempt) and `TestQuestionResult` (an answer)
  with their repositories and services (tables added to `V1`). Deleting a test, soft or hard, deletes its questions,
  attempts and answers; deleting an attempt deletes its answers; deleting a language profile soft deletes them too.
- `LanguageString` is read only and carries metadata: a list of `LanguageStringMetadata` (type, `[start, end)` over the raw
  string, a string value whose meaning belongs to the type) kept apart from the text. Create it with
  `LanguageString.builder()` / `LanguageString.of(...)`; any number of metadata may be added. For Japanese,
  `Furigana` adds and reads metadata of the type `furigana` (the value is the reading, not checked: a model may answer
  with anything).
- Question groups: `TestQuestionGroup` (a group of questions of a test that share something, e.g. a text to read; the
  questions are ordered by the ordinal of the group, then their own) with `TestQuestionGroupService`. Like questions,
  groups have a factory (`TestQuestionGroupFactory`, resolved by `TestQuestionGroupFactoryRegistry`; built-in
  `PlainGroupFactory` and `ReadingTextGroupFactory`), so extensions can add group types (audio...). Deleting a test
  deletes its groups too (table `test_question_groups` and column `test_questions.questionGroup_id` in `V1`).
- Test questions: `TestQuestion.testQuestionFactory` names the `TestQuestionFactory` (`SingleChoiceFactory`, `MultipleChoiceFactory`,
  `ReadingComprehensionWithSingleChoiceFactory`, `ReadingComprehensionWithMultipleChoicesFactory`) and `TestQuestionFactoryRegistry` resolves and caches factories by class name. Extensions register
  their own factories with `register` and remove them with `unregister` when unloaded.
- `@ExtendedAttribute(inject = true)` also works on POJO fields (and lists, maps...), not only on `JsonObject`: they are
  converted with Gson, an object is flattened by its top-level properties.
- Extension **Gemma 4 tools fix** (`lingollm/plugins/gemma4toolsfix`), ported from Marginalia: results of function calls
  go to Gemma 4 models with the role `tool_responses`.
- Language profiles: the **Learning** tab has a selector of the user's profiles (a template - Japanese or German -
  with a name and a note, flags included) and a **+** button that opens the **New language** dialog. A user without a
  profile has to create the first one before the workspace is usable. The last profile is remembered
  (`UserSetting.lastLanguage`) and opened at the next login (`Language`, `Languages`, `LanguageService`,
  `LanguagePart`, `LanguageCreationDialog`, `LanguageUI`).
- The language profile of the session is the session-scoped bean `language`, defined like `user`;
  `LanguageBoundServiceImpl.save` gives a new object without a language the one of the session
  (`LanguageBoundEntity`, `LanguageBoundService`).
- Free chats (data model only, no screen yet): `FreeChat` and `FreeChatMessage` - messages form a tree through `parent`
  and their attachments are loose links (resource uuid and caption in the extended content, no foreign key to the
  resource). Soft delete of a profile or a chat takes the owned chats and messages with it; a message can be deleted
  permanently from the trash, its children move to its parent.
- From Marginalia: users and login with saved logins, throttling and administrators; inference providers (OpenAI
  compatible, API keys encrypted, *Test Connection*), shared providers with per-user token limits and usage; the
  Resources, Trash and Admin tabs (users, database backups on a schedule, cleanup of deleted data, extensions); OSGi
  extensions with `@Extendable` hooks and `@PublicAPI` signatures; the desktop app, the Docker image and the phone
  layout.
- Localization: the keys of the interface (`L`) and an English bundle (`LocalizationBundleEN`) with only the keys the
  code uses; other languages of the interface come as extensions.
- Inference layer, ported from Marginalia without its protocols: `InferenceService.stream` (chunks of reasoning and
  response, cancellable; a JSON schema can be passed to the provider, unchecked), `infer` (the whole answer, optionally checked against a JSON schema or read into a class with
  `@LLMSchema`/`@LLMField`) and function calls for both (`FunctionRegistry`, `@LLMFunction`, tool role resolvers). Each
  request checks the limit of the user on a shared AI and counts its tokens (reported by the provider or estimated
  locally with jtokkit).
- Migration `V1` (all tables).

- The grade test is recorded while it is taken: `TestResultService.start(test)` creates the `TestResult` as soon as the
  test is written, and the `TestDialog` (new constructor `TestDialog(test, result)`, `setOnClose`) records the answer of a
  question when it is left (`TestQuestionResultService.record(result, question, answer, dontKnow)`: the first answer
  creates the `TestQuestionResult`, going back rewrites it). `TestResult.finished` is set on Finish.
- An unfinished grade test can be continued after a refresh or a closed window: **Take Grade Test** is replaced by
  **Continue Grade Test** (opens at the first question without an answer, the other answers are put back with the new
  `TestQuestionHandler.setAnswer(String)`, which does nothing by default and is implemented by the single choice, multiple
  choice, writing and word order questions) and **Discard Grade Test** (deletes the test with its attempt and answers).
  `TestResultService.findUnfinished(language, type)`. New texts `LABEL_CONTINUE_GRADE_TEST`, `LABEL_DISCARD_GRADE_TEST`,
  `MSG_CONFIRM_LEAVE_RECORDED_TEST`, `MSG_CONFIRM_DISCARD_GRADE_TEST`.
- `Test.type` (`TestType`: `GRADE`, `REGULAR`, column `test_type`): `createGaugeTest` makes a `GRADE` test, `createTest` a
  `REGULAR` one. `TestResult.finished` (column). Both are in `V1` (the database has no release yet): a development
  database needs to be created again, `dev.sql` has the column `test_type` of its test.

### Changed

- Japanese furigana is no longer part of the generation of the questions: the model writes the questions as plain texts
  (`getLanguageStringSchema()` of Japanese has only `rawString`, the furigana check of `checkLanguageString` is gone) and
  after every accepted batch a pass of its own adds the furigana to the texts that have kanji, before the next batch is
  asked for. The model sees only those texts (the same text once) and does not write them again: it lists the words with
  kanji in the order of the text, `{"word", "reading"}`, picking the readings from the context, and the application finds
  every word in the text (so a dropped `。` or an added `|` can not fail the pass), takes kana the reading repeats at the ends
  of a word out of the furigana, ignores a word that is not in the text (named in the message when a kanji has no reading), gives a word that comes again the reading of its entry and rejects kanji without a reading,
  a `targetKanji` split into more entries. The pass is asked again up to
  `MAX_ATTEMPTS` times. New prompt
  `LanguageDefaults.getAnnotateTextsPrompt` / `LanguageService.getAnnotateTextsPrompt(language)` (Handlebars: `texts`,
  `textCount`, `wholeWords`, `problems`; blank for a language without the pass, like German) and the hooks
  `needsAnnotation`, `annotatedTextSchema`, `wholeWords`, `readAnnotatedText` in `BaseLanguageDefinitionService`.
  `Furigana.hideOver` hides the furigana over the kanji of the target (`Furigana.kanjiCore`), so 食べる is hidden by the
  furigana over 食.

- The word order question is written by the model as one `sentence` cut by `|` (`Ich|heisse|Anna.`) instead of a list of
  `segments`; the application makes the segments (the stored data is the same, the program still shuffles them). New
  `TestQuestionHandler.normalizeLlmData(JsonObject)` (does nothing by default, called on what the model wrote before it is
  read; question kinds of extensions can use it for a simpler form too).

- The model is asked for questions of one kind at a time: `BaseLanguageDefinitionService` groups the requests of a test by
  their kind and cuts every group into batches of at most `BATCH_SIZE` (the questions keep their places in the test), so the
  schema of a request has no choice between kinds (providers like Gemini do not keep the `type` and the body of the
  alternatives of a `oneOf` together and wrote the body of one kind for all). The log and the reasoning of the test name the
  questions of a batch (`Questions 1, 3, 5`).
- The prompt of the test no longer lists the kinds of questions itself: every handler describes its kind
  (`TestQuestionHandler.getLlmDescription()`, empty by default, so kinds of extensions keep working) and the prompt
  inserts the description of the kind asked for (`{{#each kinds}}`, variable `kinds` with `name` and `description`;
  `promptVariables` takes the handlers of the kinds). The Japanese prompt lost its paragraph about kanji questions, it is
  the description of `KanjiReadingHandler` now.

### Known issues

- Taking the grade test does not judge the answers yet, so the level is not set by it.
- The test dialog and the rendering of the questions are checked by tests of the component tree only, not in a browser.
- There is no chat or lesson screen: nothing in the application sends requests yet (the inference layer is there and
  tested), so no token limit is applied and no usage is counted in practice.
- The learnable languages (Japanese, German) have an empty screen; their defaults (`LanguageDefaults`) are empty.

[Unreleased]: https://github.com/Enerccio/LingoLLM/commits/master
