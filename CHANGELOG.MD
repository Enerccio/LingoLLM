# Changelog

All notable changes to LingoLLM are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

First development version. The platform is taken from [Marginalia](https://github.com/Enerccio/Marginalia) (without
anything specific to its stories); the database starts at `V1`.

### Added

- Tests: language bound entities `Test`, `TestQuestion`, `TestResult` (an attempt) and `TestQuestionResult` (an answer)
  with their repositories and services (tables added to `V1`). Deleting a test, soft or hard, deletes its questions,
  attempts and answers; deleting an attempt deletes its answers; deleting a language profile soft deletes them too.
- Question groups: `TestQuestionGroup` (a group of questions of a test that share something, e.g. a text to read; the
  questions are ordered by the ordinal of the group, then their own) with `TestQuestionGroupService`. Like questions,
  groups have a factory (`TestQuestionGroupFactory`, resolved by `TestQuestionGroupFactoryRegistry`; built-in
  `PlainGroupFactory` and `ReadingTextGroupFactory`), so extensions can add group types (audio...). Deleting a test
  deletes its groups too (table `test_question_groups` and column `test_questions.questionGroup_id` in `V1`).
- Test questions: `TestQuestion.factory` names the `TestQuestionFactory` (`SingleChoiceFactory`, `MultipleChoiceFactory`,
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

### Known issues

- There is no chat or lesson screen: nothing in the application sends requests yet (the inference layer is there and
  tested), so no token limit is applied and no usage is counted in practice.
- The learnable languages (Japanese, German) have an empty screen; their defaults (`LanguageDefaults`) are empty.

[Unreleased]: https://github.com/Enerccio/LingoLLM/commits/master
