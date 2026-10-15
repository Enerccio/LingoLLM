package com.github.enerccio.lingollm.domain.factories.impl;

import com.github.enerccio.lingollm.domain.factories.TestQuestionFactory;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionHandler;
import com.github.enerccio.lingollm.ui.lang.languages.common.impl.SingleChoiceHandler;

public class SingleChoiceFactory implements TestQuestionFactory {

    @Override
    public TestQuestionHandler<?> createHandler(Test test, TestQuestion testQuestion) {
        return SingleChoiceHandler.create(test, testQuestion);
    }
}
