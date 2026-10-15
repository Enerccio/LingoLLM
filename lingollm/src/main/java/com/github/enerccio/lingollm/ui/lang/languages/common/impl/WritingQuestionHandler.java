package com.github.enerccio.lingollm.ui.lang.languages.common.impl;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionHandler;
import com.github.enerccio.lingollm.loc.L;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.textfield.TextArea;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Configurable;

/**
 * The learner writes an answer of their own. There is no single right answer to compare with, the model grades it
 * ({@code GradedBy.LLM}) against the sample answer.
 */
@Configurable
public class WritingQuestionHandler extends TestQuestionHandler<WritingQuestionData> {

    @Override
    public String getLlmDescription() {
        return "The learner writes an answer of their own, \"sampleAnswer\" is one good answer.";
    }

    private TextArea answerField;

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

    @Override
    public Component render() {
        answerField = new TextArea(loc.getValue(L.LABEL_YOUR_ANSWER));
        answerField.setWidthFull();
        answerField.setMinRows(3);
        return renderQuestion(answerField);
    }

    /**
     * @return what the learner wrote, null when nothing
     */
    @Override
    public String getAnswer() {
        return answerField == null ? null : StringUtils.trimToNull(answerField.getValue());
    }

    @Override
    public void setAnswer(String answer) {
        if (answerField != null && answer != null) {
            answerField.setValue(answer);
        }
    }
}
