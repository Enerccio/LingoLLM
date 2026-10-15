package com.github.enerccio.lingollm.ui.lang.languages.common;

/**
 * What a group shows to all of its questions. Group types that need more (a text, an audio) derive from it.
 */
public class TestQuestionGroupData {

    private LanguageString instructions;

    public LanguageString getInstructions() {
        return instructions;
    }

    public void setInstructions(LanguageString instructions) {
        this.instructions = instructions;
    }
}
