package com.github.enerccio.lingollm.domain.repository;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionResult;
import com.github.enerccio.lingollm.domain.model.impl.TestResult;

import java.util.List;

public interface TestQuestionResultRepository extends LanguageBoundRepository<TestQuestionResult> {

    /**
     * @return the answers of the attempt, oldest first
     */
    List<TestQuestionResult> findByTestResult(TestResult result) throws Exception;

    /**
     * Soft deletes the answers of the attempt with one bulk update.
     */
    int softDeleteByTestResult(TestResult result) throws Exception;

    /**
     * Permanently deletes the answers of the attempt with one bulk delete.
     */
    int hardDeleteByTestResult(TestResult result) throws Exception;

    /**
     * Soft deletes the answers of all attempts at the test with one bulk update.
     */
    int softDeleteByTest(Test test) throws Exception;

    /**
     * Permanently deletes the answers of all attempts at the test with one bulk delete.
     */
    int hardDeleteByTest(Test test) throws Exception;

}
