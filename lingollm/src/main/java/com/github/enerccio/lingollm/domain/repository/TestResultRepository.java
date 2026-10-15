package com.github.enerccio.lingollm.domain.repository;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestResult;

import java.util.List;

public interface TestResultRepository extends LanguageBoundRepository<TestResult> {

    /**
     * @return the attempts at the test, oldest first
     */
    List<TestResult> findByTest(Test test) throws Exception;

    /**
     * Soft deletes the attempts at the test with one bulk update.
     */
    int softDeleteByTest(Test test) throws Exception;

    /**
     * Permanently deletes the attempts at the test with one bulk delete.
     */
    int hardDeleteByTest(Test test) throws Exception;

}
