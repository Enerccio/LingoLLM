package com.github.enerccio.lingollm.ui.lang.languages.common.impl;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionHandler;
import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.ui.components.LanguageStringComponent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import org.springframework.beans.factory.annotation.Configurable;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * Pick one of the choices. Question types that add something to the question (a text to read) derive from it with
 * their own data class.
 */
@Configurable
public class SingleChoiceHandler<D extends SingleChoiceData> extends TestQuestionHandler<D> {

    @Override
    public String getLlmDescription() {
        return "Pick one of the choices, \"correctAnswer\" is the index of the correct one.";
    }

    private final Class<D> type;
    private final Supplier<D> empty;

    private RadioButtonGroup<Integer> choicesGroup;

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

    /**
     * The instructions, what {@link #renderPassage(SingleChoiceData)} adds and the choices as radio buttons.
     */
    @Override
    public Component render() {
        D data = getTestData(getType());
        List<LanguageString> choices = data.getChoices() == null ? List.of() : data.getChoices();

        choicesGroup = new RadioButtonGroup<>();
        choicesGroup.addClassName(CssStyles.TEST_CHOICES);
        choicesGroup.addThemeVariants(RadioGroupVariant.LUMO_VERTICAL);
        choicesGroup.setItems(IntStream.range(0, choices.size()).boxed().toList());
        choicesGroup.setRenderer(new ComponentRenderer<>(index -> new LanguageStringComponent(choices.get(index))));
        return renderQuestion(renderPassage(data), choicesGroup);
    }

    /**
     * What the question is about, shown between the instructions and the choices. Nothing by default; a question
     * that adds a text derives from this handler and shows it here.
     *
     * @return null for nothing
     */
    protected Component renderPassage(D data) {
        return null;
    }

    /**
     * @return the index of the chosen choice, null when none is
     */
    @Override
    public String getAnswer() {
        return choicesGroup == null || choicesGroup.getValue() == null ? null : String.valueOf(choicesGroup.getValue());
    }

    @Override
    public void setAnswer(String answer) {
        if (choicesGroup == null || answer == null) {
            return;
        }
        try {
            int index = Integer.parseInt(answer.strip());
            if (choicesGroup.getListDataView().getItems().anyMatch(item -> item == index)) {
                choicesGroup.setValue(index);
            }
        } catch (NumberFormatException e) {
            // not an answer of this question
        }
    }
}
