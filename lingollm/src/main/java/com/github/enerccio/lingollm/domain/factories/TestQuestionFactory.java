package com.github.enerccio.lingollm.domain.factories;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionHandler;

public interface TestQuestionFactory {

    TestQuestionHandler<?> createHandler(Test test, TestQuestion testQuestion);

}
