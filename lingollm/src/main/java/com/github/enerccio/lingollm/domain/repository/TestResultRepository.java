package com.github.enerccio.lingollm.domain.repository;

import com.github.enerccio.lingollm.domain.collections.TestType;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestResult;

import com.github.enerccio.lingollm.domain.security.model.User;

import java.util.List;

public interface TestResultRepository extends LanguageBoundRepository<TestResult> {

    /**
     * @return the attempts at the test, oldest first
     */
    List<TestResult> findByTest(Test test) throws Exception;

    /**
     * @return the attempts of the user in the language profile at tests of the type that are not finished, newest first
     */
    List<TestResult> findUnfinished(User user, Language language, TestType type) throws Exception;

    /**
     * Soft deletes the attempts at the test with one bulk update.
     */
    int softDeleteByTest(Test test) throws Exception;

    /**
     * Permanently deletes the attempts at the test with one bulk delete.
     */
    int hardDeleteByTest(Test test) throws Exception;

}
