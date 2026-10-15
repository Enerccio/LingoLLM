package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.collections.TestType;
import com.github.enerccio.lingollm.domain.model.impl.Language;
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
     * Starts an attempt at the test: the result is created at once (not finished), so the answers can be recorded as
     * they are given and the attempt continued later.
     */
    TestResult start(Test test) throws Exception;

    /**
     * @return the newest attempt of the current user in the language profile at a test of the type that is not
     * finished, with its test; null when there is none
     */
    TestResult findUnfinished(Language language, TestType type) throws Exception;

    /**
     * Deletes the attempts at the test (used when the test is deleted), without their answers: delete those with
     * {@link TestQuestionResultService#deleteForTest} first.
     */
    int deleteForTest(Test test, boolean hard) throws Exception;

}
