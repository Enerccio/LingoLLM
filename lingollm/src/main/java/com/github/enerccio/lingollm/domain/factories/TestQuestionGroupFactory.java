package com.github.enerccio.lingollm.domain.factories;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionGroup;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionGroupHandler;

public interface TestQuestionGroupFactory {

    TestQuestionGroupHandler<?> createHandler(Test test, TestQuestionGroup group);

}
