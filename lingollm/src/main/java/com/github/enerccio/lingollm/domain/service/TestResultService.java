package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestResult;
import com.github.enerccio.lingollm.domain.repository.TestResultRepository;

import java.util.List;

public interface TestResultService extends LanguageBoundService<TestResult, TestResultRepository> {

    /**
     * @return the attempts of the current user at the test, oldest first
     */
    List<TestResult> findByTest(Test test) throws Exception;

    /**
     * Deletes the attempts at the test (used when the test is deleted), without their answers: delete those with
     * {@link TestQuestionResultService#deleteForTest} first.
     */
    int deleteForTest(Test test, boolean hard) throws Exception;

}
