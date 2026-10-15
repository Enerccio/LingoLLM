package com.github.enerccio.lingollm.domain.service.impl;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.domain.repository.TestQuestionRepository;
import com.github.enerccio.lingollm.domain.service.TestQuestionService;
import com.github.enerccio.lingollm.domain.traits.CommonTx;
import com.github.enerccio.lingollm.domain.traits.CommonTxReadOnly;

import java.util.List;

public class TestQuestionServiceImpl extends LanguageBoundServiceImpl<TestQuestion, TestQuestionRepository>
        implements TestQuestionService {

    @Override
    @CommonTx
    public int deleteForTest(Test test, boolean hard) throws Exception {
        return hard ? getRepository().hardDeleteByTest(test) : getRepository().softDeleteByTest(test);
    }

    @Override
    @CommonTxReadOnly
    public List<TestQuestion> findByTest(Test test) throws Exception {
        if (test == null || test.getOwner() == null || !test.getOwner().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Not a test of the current user");
        }
        return getRepository().findByTest(test);
    }

}
