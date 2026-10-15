package com.github.enerccio.lingollm.ui.lang.languages.common.impl;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionHandler;
import org.springframework.beans.factory.annotation.Configurable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Pick one of the choices. Question types that add something to the question (a text to read) derive from it with
 * their own data class.
 */
@Configurable
public class SingleChoiceHandler<D extends SingleChoiceData> extends TestQuestionHandler<D> {

    private final Class<D> type;
    private final Supplier<D> empty;

    protected SingleChoiceHandler(Test test, TestQuestion testQuestion, Class<D> type, Supplier<D> empty) {
        super(test, testQuestion);
        this.type = type;
        this.empty = empty;
    }

    public static SingleChoiceHandler<SingleChoiceData> create(Test test, TestQuestion testQuestion) {
        return new SingleChoiceHandler<>(test, testQuestion, SingleChoiceData.class, SingleChoiceData::new);
    }

    @Override
    protected D createEmpty() {
        return empty.get();
    }

    @Override
    protected Class<D> getType() {
        return type;
    }

    @Override
    protected void check(D data) {
        if (data.getChoices() == null || data.getChoices().size() < 2) {
            throw new IllegalArgumentException("A single choice question needs at least two choices");
        }
        if (data.getCorrectAnswer() < 0 || data.getCorrectAnswer() >= data.getChoices().size()) {
            throw new IllegalArgumentException("The correct choice has to be one of the choices");
        }
    }

    public void setChoices(List<LanguageString> choices, int correctChoice) {
        if (choices == null || correctChoice < 0 || correctChoice >= choices.size()) {
            throw new IllegalArgumentException("The correct choice has to be one of the choices");
        }
        D data = getTestData(getType());
        data.setCorrectAnswer(correctChoice);
        data.setChoices(choices);
        setTestData(data);
    }
}
