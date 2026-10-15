package com.github.enerccio.lingollm.ui.lang.languages.common;

import com.github.enerccio.lingollm.domain.traits.LLMField;

public class TestQuestionData {

    @LLMField(description = "What the learner is asked to do")
    private LanguageString instructions;

    public LanguageString getInstructions() {
        return instructions;
    }

    public void setInstructions(LanguageString instructions) {
        this.instructions = instructions;
    }
}
