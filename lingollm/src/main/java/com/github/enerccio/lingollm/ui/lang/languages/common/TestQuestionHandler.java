package com.github.enerccio.lingollm.ui.lang.languages.common;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.domain.service.TestQuestionService;
import com.github.enerccio.lingollm.domain.service.TestService;
import com.github.enerccio.lingollm.domain.service.structured.StructuredOutputs;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.ui.components.LanguageStringComponent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

@Configurable
public abstract class TestQuestionHandler<DATA_CLASS extends TestQuestionData> {
    private static final Gson gson = new GsonBuilder().create();
    private static final String TEST_DATA = "$testData";

    @Autowired
    private TestQuestionService testQuestionService;

    @Autowired
    private TestService testService;

    @Autowired
    protected Localization loc;

    protected Test test;
    protected TestQuestion testQuestion;

    public TestQuestionHandler(Test test, TestQuestion testQuestion) {
        this.test = test;
        this.testQuestion = testQuestion;
    }

    protected abstract DATA_CLASS createEmpty();
    protected abstract Class<DATA_CLASS> getType();

    /**
     * The JSON schema of what the model has to write for a question of this kind: the data of the question without
     * anything the server fills in. Built from the data class ({@code @LLMField}); a handler whose question needs
     * something else from the model overrides it.
     */
    public ObjectNode getLlmSchema() {
        return StructuredOutputs.schemaOf(getType());
    }

    /**
     * The name the model writes as the {@code type} of a question to say which kind it is, see
     * {@code TestDefinitionSchemaGenerator}. Unique among the handlers offered to the model.
     */
    public String getLlmTypeName() {
        return getClass().getSimpleName();
    }

    /**
     * What the model has to know about this kind of question to write one, for its prompt: what the learner does and what
     * the properties of {@link #getLlmSchema()} mean, in English. Empty by default.
     */
    public String getLlmDescription() {
        return "";
    }

    /**
     * Turns what the model wrote into the properties of the data class, in place, when {@link #getLlmSchema()} asks for
     * something easier to write than the data is (a sentence cut by {@code |} instead of a list of texts). Called before
     * {@link #setLlmData}, and before anything else reads the question. Does nothing by default.
     */
    public void normalizeLlmData(JsonObject data) {
    }

    /**
     * Sets the data of the question from what the model wrote (the properties of {@link #getLlmSchema()}, without the
     * {@code type}).
     *
     * @throws IllegalArgumentException when it can't be read or {@link #check} refuses it
     */
    public void setLlmData(JsonObject data) {
        DATA_CLASS read;
        try {
            read = gson.fromJson(data, getType());
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("The data of the question can not be read: " + e.getMessage(), e);
        }
        if (read == null) {
            throw new IllegalArgumentException("The question has no data");
        }
        check(read);
        setTestData(read);
    }

    /**
     * Refuses data a question of this kind can't have (an index outside of the choices...). Does nothing by default.
     *
     * @throws IllegalArgumentException with what is wrong
     */
    protected void check(DATA_CLASS data) {
    }

    protected DATA_CLASS getTestData(Class<DATA_CLASS> clazz) {
        JsonObject attributes = testQuestion.getAttributes();
        if (!attributes.has(TEST_DATA)) {
            return createEmpty();
        }
        return gson.fromJson(attributes.get(TEST_DATA), clazz);
    }

    protected void setTestData(DATA_CLASS testData) {
        if (testData == null) {
            setTestData(createEmpty());
        } else {
            testQuestion.getAttributes().remove(TEST_DATA);
            testQuestion.getAttributes().add(TEST_DATA, gson.toJsonTree(testData));
        }
    }

    /**
     * Builds what the learner sees and answers: the instructions, what the question is about and the inputs for the
     * answer, in a component that can be slotted into a test dialog (it fills the width it is given and wraps, so it
     * works on a phone too). The handler keeps the inputs, so {@link #getAnswer()} reads what the learner entered into
     * the component made by the last call.
     */
    public abstract Component render();

    /**
     * The answer the learner gave in the component made by {@link #render()}, as it is stored in
     * {@code TestQuestionResult.answer}: the form depends on the kind of the question (an index, a JSON list of
     * indexes, a text).
     *
     * @return null when the learner has not answered (or {@link #render()} was not called)
     */
    public abstract String getAnswer();

    /**
     * Fills the inputs of the component made by the last {@link #render()} with an answer that was recorded, the other
     * way round than {@link #getAnswer()}: the learner continues a test they left. An answer that does not fit the
     * question is ignored. Does nothing by default.
     *
     * @param answer see {@link #getAnswer()}, null for nothing
     */
    public void setAnswer(String answer) {
    }

    /**
     * The question laid out: its instructions first, then the parts (null ones are left out).
     */
    protected Component renderQuestion(Component... parts) {
        Div question = new Div();
        question.addClassName(CssStyles.TEST_QUESTION);
        LanguageString instructions = getTestData(getType()).getInstructions();
        if (instructions != null) {
            question.add(new LanguageStringComponent(instructions));
        }
        for (Component part : parts) {
            if (part != null) {
                question.add(part);
            }
        }
        return question;
    }

    /**
     * A text the question is about (a text to read, a sentence), set apart from the rest.
     */
    protected Component passage(LanguageString text) {
        return passage(new LanguageStringComponent(text));
    }

    protected Component passage(Component content) {
        Div passage = new Div(content);
        passage.addClassName(CssStyles.TEST_PASSAGE);
        return passage;
    }

    public void setInstructions(LanguageString instructions) {
        DATA_CLASS data = getTestData(getType());
        data.setInstructions(instructions);
        setTestData(data);
    }

    public void save() throws Exception {
        testQuestion = testQuestionService.save(testQuestion);
    }

    public void refresh() throws Exception {
        test = testService.find(test);
        testQuestion = testQuestionService.find(testQuestion);
    }

}
