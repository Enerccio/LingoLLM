package com.github.enerccio.lingollm.ui.lang.languages.common.impl;

import com.github.enerccio.lingollm.domain.traits.LLMField;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;

public class ReadingMultipleChoiceData extends MultipleChoiceData {

    @LLMField(description = "The text the question is about, one block")
    private LanguageString text;

    public LanguageString getText() {
        return text;
    }

    public void setText(LanguageString text) {
        this.text = text;
    }
}
