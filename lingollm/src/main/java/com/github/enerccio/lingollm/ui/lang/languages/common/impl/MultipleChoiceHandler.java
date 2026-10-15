package com.github.enerccio.lingollm.ui.lang.languages.common.impl;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionHandler;
import org.springframework.beans.factory.annotation.Configurable;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Pick any of the choices, none of them can be the right answer too. Question types that add something to the
 * question (a text to read) derive from it with their own data class.
 */
@Configurable
public class MultipleChoiceHandler<D extends MultipleChoiceData> extends TestQuestionHandler<D> {

    private final Class<D> type;
    private final Supplier<D> empty;

    protected MultipleChoiceHandler(Test test, TestQuestion testQuestion, Class<D> type, Supplier<D> empty) {
        super(test, testQuestion);
        this.type = type;
        this.empty = empty;
    }

    public static MultipleChoiceHandler<MultipleChoiceData> create(Test test, TestQuestion testQuestion) {
        return new MultipleChoiceHandler<>(test, testQuestion, MultipleChoiceData.class, MultipleChoiceData::new);
    }

    @Override
    protected D createEmpty() {
        return empty.get();
    }

    @Override
    protected Class<D> getType() {
        return type;
    }

    /**
     * @param correctChoices indexes of the correct choices, may be empty; each choice at most once
     */
    public void setChoices(List<LanguageString> choices, List<Integer> correctChoices) {
        if (choices == null || correctChoices == null) {
            throw new IllegalArgumentException("Choices and correct choices are required");
        }
        for (Integer index : correctChoices) {
            if (index == null || index < 0 || index >= choices.size()) {
                throw new IllegalArgumentException("The correct choices have to be among the choices");
            }
        }
        if (new TreeSet<>(correctChoices).size() != correctChoices.size()) {
            throw new IllegalArgumentException("A choice can be correct only once");
        }
        D data = getTestData(getType());
        data.setChoices(choices);
        data.setCorrectAnswers(new ArrayList<>(new TreeSet<>(correctChoices)));
        setTestData(data);
    }
}
