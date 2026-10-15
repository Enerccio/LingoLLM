package com.github.enerccio.lingollm.ui.lang.languages.common.impl;

import com.github.enerccio.lingollm.domain.traits.LLMField;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionData;

import java.util.List;

public class SingleChoiceData extends TestQuestionData {

    @LLMField(minItems = 2, description = "The choices the learner picks from")
    private List<LanguageString> choices;
    @LLMField(minimum = 0, description = "Index of the correct choice in choices, counted from 0")
    private int correctAnswer;

    public List<LanguageString> getChoices() {
        return choices;
    }

    public void setChoices(List<LanguageString> choices) {
        this.choices = choices;
    }

    public int getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(int correctAnswer) {
        this.correctAnswer = correctAnswer;
    }
}
