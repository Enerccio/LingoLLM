package com.github.enerccio.lingollm.domain.repository;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionGroup;

import java.util.List;

public interface TestQuestionGroupRepository extends LanguageBoundRepository<TestQuestionGroup> {

    /**
     * @return the groups of the test in the order of the test
     */
    List<TestQuestionGroup> findByTest(Test test) throws Exception;

    /**
     * Soft deletes the groups of the test with one bulk update.
     */
    int softDeleteByTest(Test test) throws Exception;

    /**
     * Permanently deletes the groups of the test with one bulk delete.
     */
    int hardDeleteByTest(Test test) throws Exception;

}
