package com.github.enerccio.lingollm.ui.lang.languages.common.impl;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import org.springframework.beans.factory.annotation.Configurable;

/**
 * A multiple choice question about a text, the text is one block.
 */
@Configurable
public class ReadingComprehensionWithMultipleChoicesHandler extends MultipleChoiceHandler<ReadingMultipleChoiceData> {

    public ReadingComprehensionWithMultipleChoicesHandler(Test test, TestQuestion testQuestion) {
        super(test, testQuestion, ReadingMultipleChoiceData.class, ReadingMultipleChoiceData::new);
    }

    public void setText(LanguageString text) {
        ReadingMultipleChoiceData data = getTestData(getType());
        data.setText(text);
        setTestData(data);
    }
}
