-- LingoLLM initial schema

create table users
(
    id              bigint      not null
        primary key,
    creation        timestamp,
    is_deleted      boolean     not null,
    modification    timestamp,
    uuid            varchar(36) not null
        unique,
    fullName     clob,
    isAdmin      boolean     not null,
    login        varchar(64)
        unique,
    passwordHash clob,
    savedLogins  clob,
    failedLogins int         not null default 0,
    lockedUntil  bigint      not null default 0
);

create table users_SEQ
(
    next_val bigint
);
INSERT INTO users_SEQ (next_val) VALUES (1);

create table settings
(
    id              bigint      not null
        primary key,
    creation        timestamp,
    is_deleted      boolean     not null,
    modification    timestamp,
    uuid            varchar(36) not null
        unique,
    extendedContent blob,
    _fulltext       clob,
    DTYPE           varchar(255),
    key             varchar(64),
    userId          bigint
);

create table settings_SEQ
(
    next_val bigint
);
INSERT INTO settings_SEQ (next_val) VALUES (1);

create index ix_settings_key_user on settings (key, userId);

create table ais
(
    id              bigint      not null
        primary key,
    creation        timestamp,
    is_deleted      boolean     not null,
    modification    timestamp,
    uuid            varchar(36) not null
        unique,
    extendedContent blob,
    _fulltext       clob,
    ai_type             tinyint,
    enabledReasoning    boolean,
    jailbreak           clob,
    maxCompletionTokens integer,
    maxContext          integer,
    name                clob,
    needsJailbreak      boolean,
    reasoningEffort     varchar(255),
    shared              boolean     not null default false,
    userId          bigint
);

create table ais_SEQ
(
    next_val bigint
);
INSERT INTO ais_SEQ (next_val) VALUES (1);

create index ix_ais_user on ais (userId, is_deleted);
create index ai_is_deleted_idx on ais (is_deleted);
create index ai_type_idx on ais (ai_type);
create index ix_ais_shared on ais (shared, is_deleted);

create table ais_openaicompat
(
    apiKey    clob,
    model     clob,
    modelName clob,
    uri       clob,
    id        bigint not null
        primary key
);

create table resources
(
    id              bigint      not null
        primary key,
    creation        timestamp,
    is_deleted      boolean     not null,
    modification    timestamp,
    uuid            varchar(36) not null
        unique,
    hash         clob,
    mimeType     varchar(255),
    originalName clob,
    path         varchar(255),
    size         bigint      not null,
    objectId     bigint,
    clazz        varchar(255),
    userId          bigint
);

create table resources_SEQ
(
    next_val bigint
);
INSERT INTO resources_SEQ (next_val) VALUES (1);

create index ix_resources_user_hash on resources (userId, hash);
create index resource_is_deleted_ix on resources (is_deleted);
create index ix_resources_user_list on resources (userId, is_deleted, creation);
create index ix_resources_object on resources (clazz, objectId);

-- ai_id is nullable in the table only: cleanup detaches a row from its AI before it purges both
create table usages
(
    id                    bigint      not null primary key,
    creation              timestamp,
    is_deleted            boolean     not null,
    modification          timestamp,
    uuid                  varchar(36) not null unique,
    userId                bigint,
    ai_id                 bigint,
    prefillTokens         bigint      not null default 0,
    generatedTokens       bigint      not null default 0,
    requests              bigint      not null default 0,
    lastUsed              timestamp,
    periodPrefillTokens   bigint      not null default 0,
    periodGeneratedTokens bigint      not null default 0
);

create unique index ux_usages_ai_user on usages (ai_id, userId);
create index ix_usages_user on usages (userId, is_deleted);

create table usages_SEQ
(
    next_val bigint
);
INSERT INTO usages_SEQ (next_val) VALUES (1);

create table languages
(
    id              bigint      not null
        primary key,
    creation        timestamp,
    is_deleted      boolean     not null,
    modification    timestamp,
    uuid            varchar(36) not null
        unique,
    extendedContent blob,
    _fulltext       clob,
    language        varchar(255),
    nameOverride    clob,
    userId          bigint
);

create table languages_SEQ
(
    next_val bigint
);
INSERT INTO languages_SEQ (next_val) VALUES (1);

create index ix_languages_user on languages (userId, is_deleted);

create table free_chats
(
    id              bigint      not null
        primary key,
    creation        timestamp,
    is_deleted      boolean     not null,
    modification    timestamp,
    uuid            varchar(36) not null
        unique,
    extendedContent blob,
    _fulltext       clob,
    language_id     bigint,
    userId          bigint
);

create table free_chats_SEQ
(
    next_val bigint
);
INSERT INTO free_chats_SEQ (next_val) VALUES (1);

create index ix_free_chats_language on free_chats (userId, language_id, is_deleted);

create table free_chat_messages
(
    id              bigint      not null
        primary key,
    creation        timestamp,
    is_deleted      boolean     not null,
    modification    timestamp,
    uuid            varchar(36) not null
        unique,
    extendedContent blob,
    _fulltext       clob,
    language_id     bigint,
    parentChat_id   bigint,
    parent_id       bigint,
    userId          bigint
);

create table free_chat_messages_SEQ
(
    next_val bigint
);
INSERT INTO free_chat_messages_SEQ (next_val) VALUES (1);

create index ix_free_chat_messages_chat on free_chat_messages (parentChat_id, creation);
create index ix_free_chat_messages_parent on free_chat_messages (parent_id);

create table tests
(
    id              bigint      not null
        primary key,
    creation        timestamp,
    is_deleted      boolean     not null,
    modification    timestamp,
    uuid            varchar(36) not null
        unique,
    extendedContent blob,
    _fulltext       clob,
    language_id     bigint,
    test_type       varchar(16) not null,
    userId          bigint
);

create table tests_SEQ
(
    next_val bigint
);
INSERT INTO tests_SEQ (next_val) VALUES (1);

create index ix_tests_language on tests (userId, language_id, is_deleted);

create table test_question_groups
(
    id              bigint      not null
        primary key,
    creation        timestamp,
    is_deleted      boolean     not null,
    modification    timestamp,
    uuid            varchar(36) not null
        unique,
    extendedContent blob,
    _fulltext       clob,
    language_id     bigint,
    ordinal         integer,
    parentTest_id   bigint,
    userId          bigint
);

create table test_question_groups_SEQ
(
    next_val bigint
);
INSERT INTO test_question_groups_SEQ (next_val) VALUES (1);

create index ix_test_question_groups_test on test_question_groups (parentTest_id, ordinal);

create table test_questions
(
    id              bigint      not null
        primary key,
    creation        timestamp,
    is_deleted      boolean     not null,
    modification    timestamp,
    uuid            varchar(36) not null
        unique,
    extendedContent blob,
    _fulltext       clob,
    language_id     bigint,
    ordinal         integer,
    parentTest_id   bigint,
    questionGroup_id bigint,
    userId          bigint
);

create table test_questions_SEQ
(
    next_val bigint
);
INSERT INTO test_questions_SEQ (next_val) VALUES (1);

create index ix_test_questions_test on test_questions (parentTest_id, ordinal);
create index ix_test_questions_group on test_questions (questionGroup_id, ordinal);

create table test_results
(
    id              bigint      not null
        primary key,
    creation        timestamp,
    is_deleted      boolean     not null,
    modification    timestamp,
    uuid            varchar(36) not null
        unique,
    extendedContent blob,
    _fulltext       clob,
    language_id     bigint,
    test_id         bigint,
    finished        boolean     not null,
    userId          bigint
);

create table test_results_SEQ
(
    next_val bigint
);
INSERT INTO test_results_SEQ (next_val) VALUES (1);

create index ix_test_results_test on test_results (test_id, creation);

create table test_question_results
(
    id              bigint      not null
        primary key,
    creation        timestamp,
    is_deleted      boolean     not null,
    modification    timestamp,
    uuid            varchar(36) not null
        unique,
    extendedContent blob,
    _fulltext       clob,
    language_id     bigint,
    testResult_id   bigint,
    question_id     bigint,
    userId          bigint
);

create table test_question_results_SEQ
(
    next_val bigint
);
INSERT INTO test_question_results_SEQ (next_val) VALUES (1);

create index ix_test_question_results_result on test_question_results (testResult_id, creation);
create index ix_test_question_results_question on test_question_results (question_id);
