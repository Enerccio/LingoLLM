package com.github.enerccio.lingollm.ui.lang.languages.common.impl;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionGroup;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionGroupData;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionGroupHandler;
import org.springframework.beans.factory.annotation.Configurable;

/**
 * A group that only has instructions: what a question that stands alone is put in.
 */
@Configurable
public class PlainGroupHandler extends TestQuestionGroupHandler<TestQuestionGroupData> {

    public PlainGroupHandler(Test test, TestQuestionGroup group) {
        super(test, group, TestQuestionGroupData.class, TestQuestionGroupData::new);
    }
}
