package com.github.enerccio.lingollm.domain.service.impl;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionGroup;
import com.github.enerccio.lingollm.domain.repository.TestQuestionGroupRepository;
import com.github.enerccio.lingollm.domain.service.TestQuestionGroupService;
import com.github.enerccio.lingollm.domain.traits.CommonTx;
import com.github.enerccio.lingollm.domain.traits.CommonTxReadOnly;

import java.util.List;

public class TestQuestionGroupServiceImpl extends LanguageBoundServiceImpl<TestQuestionGroup, TestQuestionGroupRepository>
        implements TestQuestionGroupService {

    @Override
    @CommonTx
    public int deleteForTest(Test test, boolean hard) throws Exception {
        return hard ? getRepository().hardDeleteByTest(test) : getRepository().softDeleteByTest(test);
    }

    @Override
    @CommonTxReadOnly
    public List<TestQuestionGroup> findByTest(Test test) throws Exception {
        if (test == null || test.getOwner() == null || !test.getOwner().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Not a test of the current user");
        }
        return getRepository().findByTest(test);
    }

}
