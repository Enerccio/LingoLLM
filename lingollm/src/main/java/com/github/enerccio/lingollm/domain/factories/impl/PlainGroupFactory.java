package com.github.enerccio.lingollm.domain.factories.impl;

import com.github.enerccio.lingollm.domain.factories.TestQuestionGroupFactory;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionGroup;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionGroupHandler;
import com.github.enerccio.lingollm.ui.lang.languages.common.impl.PlainGroupHandler;

public class PlainGroupFactory implements TestQuestionGroupFactory {

    @Override
    public TestQuestionGroupHandler<?> createHandler(Test test, TestQuestionGroup group) {
        return new PlainGroupHandler(test, group);
    }
}
