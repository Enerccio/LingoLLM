package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionResult;
import com.github.enerccio.lingollm.domain.model.impl.TestResult;
import com.github.enerccio.lingollm.domain.repository.TestQuestionResultRepository;

import java.util.List;

public interface TestQuestionResultService extends LanguageBoundService<TestQuestionResult, TestQuestionResultRepository> {

    /**
     * @return the answers of the attempt of the current user, oldest first
     */
    List<TestQuestionResult> findByTestResult(TestResult result) throws Exception;

    /**
     * Deletes the answers of the attempt (used when the attempt is deleted).
     */
    int deleteForTestResult(TestResult result, boolean hard) throws Exception;

    /**
     * Deletes the answers of all attempts at the test (used when the test is deleted).
     */
    int deleteForTest(Test test, boolean hard) throws Exception;

}
