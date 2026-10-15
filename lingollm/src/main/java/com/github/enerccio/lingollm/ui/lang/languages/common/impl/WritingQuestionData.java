package com.github.enerccio.lingollm.ui.lang.languages.common.impl;

import com.github.enerccio.lingollm.domain.traits.LLMField;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionData;

public class WritingQuestionData extends TestQuestionData {

    @LLMField(description = "One good answer to the question, for whoever grades the learner's answer")
    private LanguageString sampleAnswer;

    public LanguageString getSampleAnswer() {
        return sampleAnswer;
    }

    public void setSampleAnswer(LanguageString sampleAnswer) {
        this.sampleAnswer = sampleAnswer;
    }
}
