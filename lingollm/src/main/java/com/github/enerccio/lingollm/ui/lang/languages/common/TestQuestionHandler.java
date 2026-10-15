package com.github.enerccio.lingollm.ui.lang.languages.common;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.domain.service.TestQuestionService;
import com.github.enerccio.lingollm.domain.service.TestService;
import com.github.enerccio.lingollm.domain.service.structured.StructuredOutputs;
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
