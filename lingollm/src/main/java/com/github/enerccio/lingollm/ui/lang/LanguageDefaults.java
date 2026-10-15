package com.github.enerccio.lingollm.ui.lang;

/**
 * The texts a language starts with. {@link com.github.enerccio.lingollm.domain.collections.Languages} holds the defaults
 * of each learnable language; a language profile ({@code Language.defaults}) holds the ones the user changed, a value
 * that is not set (null or blank) means the default of the language is used, see
 * {@code LanguageService}.
 * <p>
 * The texts are Handlebars templates ({@code {{name}}}), see {@code TemplateService}.
 */
public class LanguageDefaults {

    /**
     * Variables of {@link #getCreateTestPrompt()}: {@code language} (name of the language), {@code levels} (list of
     * {@code name} and {@code description} of the CEFR levels), {@code languageStringRules} (how the model writes a text,
     * see {@code LanguageDefinitionService}), {@code questionCount} and {@code questions} (the JSON list of the questions
     * asked for, in order: the type and the CEFR {@code level} of each), {@code kinds} (the kinds of the questions asked for,
     * each with the {@code name} the model writes as the type and the {@code description} the handler of the kind gives,
     * see {@code TestQuestionHandler.getLlmDescription}), {@code previous} (the questions of the test
     * written so far, one line each, empty for the first request) and {@code problems} (what was wrong with the last
     * answer when the same questions are asked again, empty otherwise).
     * <p>
     * The prompt is not about any kind of test: what the test is for is in the questions asked.
     */
    private static final String CREATE_TEST_PROMPT = """
            You are an experienced {{language}} teacher. Write questions of a test for learners of {{language}}.

            Every question has a CEFR "level": its difficulty must be right for a learner of exactly that level, who can answer
            it, while a learner of the level below usually can not. The CEFR levels:
            {{#each levels}}
            - {{name}}: {{description}}
            {{/each}}

            Write exactly {{questionCount}} questions, in the order and of the types listed below. Every question has a "type" that
            must be the same as the type requested for its position. The instructions of a question are written in simple English;
            the {{language}} text of the question (choices, texts, sentences) is written in {{language}}. Every question
            must have exactly the answer that is marked as correct: no ambiguity, no trick questions, no question that
            needs knowledge outside the language. Texts to read are original, short and natural.

            {{languageStringRules}}

            The kind of question:
            {{#each kinds}}
            - {{name}}: {{description}}
            {{/each}}
            {{#if previous}}

            The test has these questions already, write different ones (other topics, other words):
            {{#each previous}}
            - {{this}}
            {{/each}}
            {{/if}}

            The questions to write:
            {{questions}}
            {{#if problems}}

            Your last answer was rejected, write all the questions again and fix this:
            {{#each problems}}
            - {{this}}
            {{/each}}
            {{/if}}

            Answer only with the JSON of the questions.
            """;

    /**
     * The second pass over the texts of the questions, for languages whose texts need more than the plain text: the
     * model gets the texts and writes what the language needs to them (Japanese: the readings of the words with kanji). Variables: {@code language}, {@code textCount}, {@code texts} (list of {@code number} and
     * {@code text}), {@code wholeWords} (words that must not be split, empty mostly) and {@code problems} (as for the
     * create test prompt).
     */
    private static final String ANNOTATE_JAPANESE_PROMPT = """
            You are a Japanese teacher. Give the readings (furigana) of the kanji in the Japanese texts below, which are used
            in a test for learners.

            For every text list the words that have kanji, in the order they are in the text: {"word": "...", "reading": "..."}.
            Do not write the text again, only the words.
            - "word" is one kanji or a word of several kanji, written exactly as in the text. The kana right after it (okurigana)
              may be in it: 食べる, 食べ and 食 are all fine, as the text has it.
            - "reading" is the reading of the word in hiragana as it is read in this very text, not any other reading it could
              have. 日本語 is {"word": "日本語", "reading": "にほんご"}.
            - Every kanji of the text is in one of the words: names, numbers (一, 二, 三) and the kanji of kana-heavy words too.
            - A word that is in the text twice has two entries.
            - Words without kanji (kana, punctuation, latin letters) are not listed.
            {{#if wholeWords}}
            - These words must each be one single entry, never split: {{#each wholeWords}}{{this}}{{#unless @last}}, {{/unless}}{{/each}}.
            {{/if}}

            Answer with {"texts": [...]}: exactly {{textCount}} items, the item of the text number 1 first, each
            {"readings": [...]}.

            The texts:
            {{#each texts}}
            {{number}}. {{text}}
            {{/each}}
            {{#if problems}}

            Your last answer was rejected, write all the texts again and fix this:
            {{#each problems}}
            - {{this}}
            {{/each}}
            {{/if}}

            Answer only with the JSON.
            """;

    public static LanguageDefaults JAPANESE;
    public static LanguageDefaults GERMAN;

    private String freeChatTemplate;
    private String createTestPrompt;
    private String annotateTextsPrompt;

    static {
        {
            JAPANESE = new LanguageDefaults();
            JAPANESE.freeChatTemplate = """
            """;
            JAPANESE.createTestPrompt = CREATE_TEST_PROMPT + """

                    Use the natural Japanese writing (kanji and kana as a Japanese writes it), never romaji. The higher the
                    level of a question, the more kanji and the harder words, the less furigana the learner is expected to need.
                    Write plain text only: the furigana are added to the texts afterwards.

                    """;
            JAPANESE.annotateTextsPrompt = ANNOTATE_JAPANESE_PROMPT;
        }
        {
            GERMAN = new LanguageDefaults();
            GERMAN.freeChatTemplate = """
            """;
            GERMAN.createTestPrompt = CREATE_TEST_PROMPT;
        }
    }

    public String getFreeChatTemplate() {
        return freeChatTemplate;
    }

    public void setFreeChatTemplate(String freeChatTemplate) {
        this.freeChatTemplate = freeChatTemplate;
    }

    /** Template of the prompt that makes the model write questions of a test. */
    public String getCreateTestPrompt() {
        return createTestPrompt;
    }

    public void setCreateTestPrompt(String createTestPrompt) {
        this.createTestPrompt = createTestPrompt;
    }

    /** Template of the prompt of the second pass over the texts of the questions, blank when the language has none. */
    public String getAnnotateTextsPrompt() {
        return annotateTextsPrompt;
    }

    public void setAnnotateTextsPrompt(String annotateTextsPrompt) {
        this.annotateTextsPrompt = annotateTextsPrompt;
    }
}
