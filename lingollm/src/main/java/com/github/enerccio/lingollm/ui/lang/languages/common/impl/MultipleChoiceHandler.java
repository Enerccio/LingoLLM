package com.github.enerccio.lingollm.ui.lang.languages.common.impl;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionHandler;
import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.ui.components.LanguageStringComponent;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.checkbox.CheckboxGroupVariant;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import org.springframework.beans.factory.annotation.Configurable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * Pick any of the choices, none of them can be the right answer too. Question types that add something to the
 * question (a text to read) derive from it with their own data class.
 */
@Configurable
public class MultipleChoiceHandler<D extends MultipleChoiceData> extends TestQuestionHandler<D> {

    @Override
    public String getLlmDescription() {
        return "Pick all the correct choices, \"correctAnswers\" holds their indexes, there may be none.";
    }

    private final Class<D> type;
    private final Supplier<D> empty;

    private CheckboxGroup<Integer> choicesGroup;

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

    @Override
    protected void check(D data) {
        if (data.getChoices() == null || data.getChoices().size() < 2) {
            throw new IllegalArgumentException("A multiple choice question needs at least two choices");
        }
        List<Integer> correct = data.getCorrectAnswers() == null ? List.of() : data.getCorrectAnswers();
        for (Integer index : correct) {
            if (index == null || index < 0 || index >= data.getChoices().size()) {
                throw new IllegalArgumentException("The correct choices have to be among the choices");
            }
        }
        if (new TreeSet<>(correct).size() != correct.size()) {
            throw new IllegalArgumentException("A choice can be correct only once");
        }
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

    /**
     * The instructions, what {@link #renderPassage(MultipleChoiceData)} adds and the choices as check boxes.
     */
    @Override
    public Component render() {
        D data = getTestData(getType());
        List<LanguageString> choices = data.getChoices() == null ? List.of() : data.getChoices();

        choicesGroup = new CheckboxGroup<>();
        choicesGroup.addClassName(CssStyles.TEST_CHOICES);
        choicesGroup.addThemeVariants(CheckboxGroupVariant.LUMO_VERTICAL);
        choicesGroup.setItems(IntStream.range(0, choices.size()).boxed().toList());
        choicesGroup.setRenderer(new ComponentRenderer<>(index -> new LanguageStringComponent(choices.get(index))));
        return renderQuestion(renderPassage(data), choicesGroup);
    }

    /**
     * What the question is about, shown between the instructions and the choices. Nothing by default.
     *
     * @return null for nothing
     */
    protected Component renderPassage(D data) {
        return null;
    }

    /**
     * @return JSON list of the indexes of the chosen choices in ascending order, {@code []} when none is chosen (that
     * is an answer too: no choice can be the correct one)
     */
    @Override
    public String getAnswer() {
        if (choicesGroup == null) {
            return null;
        }
        return new Gson().toJson(new TreeSet<>(choicesGroup.getValue()));
    }

    @Override
    public void setAnswer(String answer) {
        if (choicesGroup == null || answer == null) {
            return;
        }
        try {
            Set<Integer> chosen = new TreeSet<>();
            for (JsonElement index : JsonParser.parseString(answer).getAsJsonArray()) {
                int value = index.getAsInt();
                if (choicesGroup.getListDataView().getItems().anyMatch(item -> item == value)) {
                    chosen.add(value);
                }
            }
            choicesGroup.setValue(chosen);
        } catch (RuntimeException e) {
            // not an answer of this question
        }
    }
}
