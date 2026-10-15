package com.github.enerccio.lingollm.ui.lang.languages.common.impl;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionHandler;
import org.springframework.beans.factory.annotation.Configurable;

/**
 * The learner writes an answer of their own. There is no single right answer to compare with, the model grades it
 * ({@code GradedBy.LLM}) against the sample answer.
 */
@Configurable
public class WritingQuestionHandler extends TestQuestionHandler<WritingQuestionData> {

    public WritingQuestionHandler(Test test, TestQuestion testQuestion) {
        super(test, testQuestion);
    }

    @Override
    protected WritingQuestionData createEmpty() {
        return new WritingQuestionData();
    }

    @Override
    protected Class<WritingQuestionData> getType() {
        return WritingQuestionData.class;
    }

    @Override
    protected void check(WritingQuestionData data) {
        if (data.getSampleAnswer() == null) {
            throw new IllegalArgumentException("A writing question needs a sample answer");
        }
    }

    public void setSampleAnswer(LanguageString sampleAnswer) {
        WritingQuestionData data = getTestData(getType());
        data.setSampleAnswer(sampleAnswer);
        setTestData(data);
    }
}
