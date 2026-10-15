package com.github.enerccio.lingollm.ui.lang.languages.common.impl;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.vaadin.flow.component.Component;
import org.springframework.beans.factory.annotation.Configurable;

/**
 * A single choice question about a text, the text is one block.
 */
@Configurable
public class ReadingComprehensionWithSingleChoiceHandler extends SingleChoiceHandler<ReadingSingleChoiceData> {

    @Override
    public String getLlmDescription() {
        return "Pick one of the choices, \"correctAnswer\" is the index of the correct one. The question is about the \"text\" that comes with it, the learner reads it first.";
    }

    public ReadingComprehensionWithSingleChoiceHandler(Test test, TestQuestion testQuestion) {
        super(test, testQuestion, ReadingSingleChoiceData.class, ReadingSingleChoiceData::new);
    }

    public void setText(LanguageString text) {
        ReadingSingleChoiceData data = getTestData(getType());
        data.setText(text);
        setTestData(data);
    }

    @Override
    protected Component renderPassage(ReadingSingleChoiceData data) {
        return data.getText() == null ? null : passage(data.getText());
    }
}
