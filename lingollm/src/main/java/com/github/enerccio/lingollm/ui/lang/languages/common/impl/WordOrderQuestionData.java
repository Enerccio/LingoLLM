package com.github.enerccio.lingollm.ui.lang.languages.common.impl;

import com.github.enerccio.lingollm.domain.traits.LLMField;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionData;

import java.util.List;

public class WordOrderQuestionData extends TestQuestionData {

    @LLMField(minItems = 2, description = "The parts of one sentence in their correct order, the program shuffles them for the learner")
    private List<LanguageString> segments;

    public List<LanguageString> getSegments() {
        return segments;
    }

    public void setSegments(List<LanguageString> segments) {
        this.segments = segments;
    }
}
