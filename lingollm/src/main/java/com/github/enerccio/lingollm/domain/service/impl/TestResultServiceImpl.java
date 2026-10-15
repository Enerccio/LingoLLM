package com.github.enerccio.lingollm.domain.service.impl;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestResult;
import com.github.enerccio.lingollm.domain.repository.TestResultRepository;
import com.github.enerccio.lingollm.domain.service.TestQuestionResultService;
import com.github.enerccio.lingollm.domain.service.TestResultService;
import com.github.enerccio.lingollm.domain.traits.CommonTx;
import com.github.enerccio.lingollm.domain.traits.CommonTxReadOnly;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class TestResultServiceImpl extends LanguageBoundServiceImpl<TestResult, TestResultRepository>
        implements TestResultService {

    @Autowired
    private TestQuestionResultService questionResultService;

    /**
     * Deleting an attempt deletes (soft or hard, like the attempt) its answers.
     */
    @Override
    @CommonTx
    public TestResult delete(TestResult result, boolean hard) throws Exception {
        if (result != null && result.getId() != null) {
            questionResultService.deleteForTestResult(result, hard);
        }
        return super.delete(result, hard);
    }

    @Override
    @CommonTx
    public int deleteForTest(Test test, boolean hard) throws Exception {
        return hard ? getRepository().hardDeleteByTest(test) : getRepository().softDeleteByTest(test);
    }

    @Override
    @CommonTxReadOnly
    public List<TestResult> findByTest(Test test) throws Exception {
        if (test == null || test.getOwner() == null || !test.getOwner().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Not a test of the current user");
        }
        return getRepository().findByTest(test);
    }

}
