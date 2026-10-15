package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
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
     * Records the answer to the question in the attempt: the first answer creates the record, the next ones (the learner
     * went back) rewrite it, and a judgement of the old answer is dropped.
     *
     * @param answer   see {@code TestQuestionHandler.getAnswer()}, null when there is none
     * @param dontKnow the learner said they don't know, the answer is dropped then
     */
    TestQuestionResult record(TestResult result, TestQuestion question, String answer, boolean dontKnow) throws Exception;

    /**
     * Deletes the answers of the attempt (used when the attempt is deleted).
     */
    int deleteForTestResult(TestResult result, boolean hard) throws Exception;

    /**
     * Deletes the answers of all attempts at the test (used when the test is deleted).
     */
    int deleteForTest(Test test, boolean hard) throws Exception;

}
