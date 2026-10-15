package com.github.enerccio.lingollm.domain.repository;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;

import java.util.List;

public interface TestQuestionRepository extends LanguageBoundRepository<TestQuestion> {

    /**
     * @return the questions of the test in the order of the test: by the ordinal of the group, then by their own
     */
    List<TestQuestion> findByTest(Test test) throws Exception;

    /**
     * Soft deletes the questions of the test with one bulk update.
     */
    int softDeleteByTest(Test test) throws Exception;

    /**
     * Permanently deletes the questions of the test with one bulk delete.
     */
    int hardDeleteByTest(Test test) throws Exception;

}
