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
     * Variables of {@link #getGaugeTestPrompt()}: {@code language} (name of the language), {@code userGrade} (level the
     * user says they have, or "unknown"), {@code userGradeFineDetails} (what the user wrote about it, may be empty),
     * {@code levels} (list of {@code name} and {@code description} of the CEFR levels),
     * {@code languageStringRules} (how the model writes a text, see {@code LanguageDefinitionService}),
     * {@code questionCount} and {@code questions} (the JSON list of requested questions, in order).
     */
    private static final String GAUGE_TEST_PROMPT = """
            You are an experienced {{language}} teacher. Write a placement test that gauges the CEFR level of a learner of {{language}}.

            The learner says their level is: {{userGrade}}.
            {{#if userGradeFineDetails}}
            What the learner says about their skills: {{userGradeFineDetails}}
            {{/if}}

            The CEFR levels:
            {{#each levels}}
            - {{name}}: {{description}}
            {{/each}}

            Write exactly {{questionCount}} questions, in the order and of the types listed below. Every question has a "type" that
            must be the same as the type requested for its position. Start with easy questions and make them gradually harder, so
            that the whole range from A1 to C2 is covered and the point where the learner starts to fail shows their level.
            Do not depend on the level the learner claims. The instructions of a question are written in simple English;
            the {{language}} text of the question (choices, texts, sentences) is written in {{language}}. Every question
            must have exactly the answer that is marked as correct: no ambiguity, no trick questions, no question that
            needs knowledge outside the language. Texts to read are original, short and natural.

            {{languageStringRules}}

            Types of questions:
            - SingleChoiceHandler: pick one of the choices, "correctAnswer" is the index of the correct one.
            - MultipleChoiceHandler: pick all the correct choices, "correctAnswers" holds their indexes, there may be none.
            - ReadingComprehensionWithSingleChoiceHandler, ReadingComprehensionWithMultipleChoicesHandler: like the two above,
              about the "text" that comes with the question.
            - WritingQuestionHandler: the learner writes an answer of their own, "sampleAnswer" is one good answer.
            - WordOrderQuestionHandler: "segments" are the parts of one correct sentence in their correct order; the program
              shuffles them for the learner. Use 3 to 8 segments.

            The questions to write:
            {{questions}}

            Answer only with the JSON of the test.
            """;

    public static LanguageDefaults JAPANESE;
    public static LanguageDefaults GERMAN;

    private String freeChatTemplate;
    private String gaugeTestPrompt;

    static {
        {
            JAPANESE = new LanguageDefaults();
            JAPANESE.freeChatTemplate = """
            """;
            JAPANESE.gaugeTestPrompt = GAUGE_TEST_PROMPT + """

                    Use the natural Japanese writing (kanji and kana as a Japanese writes it), never romaji. The higher the
                    level, the more kanji, the less furigana the learner is expected to need - but the furigana of every
                    kanji is still required, it is how the application shows the text.
                    """;
        }
        {
            GERMAN = new LanguageDefaults();
            GERMAN.freeChatTemplate = """
            """;
            GERMAN.gaugeTestPrompt = GAUGE_TEST_PROMPT;
        }
    }

    public String getFreeChatTemplate() {
        return freeChatTemplate;
    }

    public void setFreeChatTemplate(String freeChatTemplate) {
        this.freeChatTemplate = freeChatTemplate;
    }

    /** Template of the prompt that makes the model write a test that gauges the level of the learner. */
    public String getGaugeTestPrompt() {
        return gaugeTestPrompt;
    }

    public void setGaugeTestPrompt(String gaugeTestPrompt) {
        this.gaugeTestPrompt = gaugeTestPrompt;
    }
}
