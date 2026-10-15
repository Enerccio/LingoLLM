package com.github.enerccio.lingollm.ui.lang.languages.common;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.domain.service.TestQuestionService;
import com.github.enerccio.lingollm.domain.service.TestService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
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
