package com.github.enerccio.lingollm.ui.lang.languages.common.impl;

import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionData;

import java.util.ArrayList;
import java.util.List;

public class MultipleChoiceData extends TestQuestionData {

    private List<LanguageString> choices;
    // indexes into the choices, ascending, empty when no choice is correct
    private List<Integer> correctAnswers = new ArrayList<>();

    public List<LanguageString> getChoices() {
        return choices;
    }

    public void setChoices(List<LanguageString> choices) {
        this.choices = choices;
    }

    public List<Integer> getCorrectAnswers() {
        return correctAnswers;
    }

    public void setCorrectAnswers(List<Integer> correctAnswers) {
        this.correctAnswers = correctAnswers;
    }
}
