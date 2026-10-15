package com.github.enerccio.lingollm.ui.lang.languages.common.impl;

import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;

public class ReadingMultipleChoiceData extends MultipleChoiceData {

    private LanguageString text;

    public LanguageString getText() {
        return text;
    }

    public void setText(LanguageString text) {
        this.text = text;
    }
}
