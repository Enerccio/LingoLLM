package com.github.enerccio.lingollm.ui.lang.languages.common.impl;

import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionData;

import java.util.List;

public class SingleChoiceData extends TestQuestionData {

    private List<LanguageString> choices;
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
