package com.github.enerccio.lingollm.domain.service.impl;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionResult;
import com.github.enerccio.lingollm.domain.model.impl.TestResult;
import com.github.enerccio.lingollm.domain.repository.TestQuestionResultRepository;
import com.github.enerccio.lingollm.domain.service.TestQuestionResultService;
import com.github.enerccio.lingollm.domain.traits.CommonTx;
import com.github.enerccio.lingollm.domain.traits.CommonTxReadOnly;

import java.util.List;

public class TestQuestionResultServiceImpl extends LanguageBoundServiceImpl<TestQuestionResult, TestQuestionResultRepository>
        implements TestQuestionResultService {

    @Override
    @CommonTx
    public int deleteForTestResult(TestResult result, boolean hard) throws Exception {
        return hard ? getRepository().hardDeleteByTestResult(result) : getRepository().softDeleteByTestResult(result);
    }

    @Override
    @CommonTx
    public int deleteForTest(Test test, boolean hard) throws Exception {
        return hard ? getRepository().hardDeleteByTest(test) : getRepository().softDeleteByTest(test);
    }

    @Override
    @CommonTxReadOnly
    public List<TestQuestionResult> findByTestResult(TestResult result) throws Exception {
        if (result == null || result.getOwner() == null || !result.getOwner().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Not a result of the current user");
        }
        return getRepository().findByTestResult(result);
    }

}
