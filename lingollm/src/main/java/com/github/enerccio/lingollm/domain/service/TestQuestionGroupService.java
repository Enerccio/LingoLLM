package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionGroup;
import com.github.enerccio.lingollm.domain.repository.TestQuestionGroupRepository;

import java.util.List;

public interface TestQuestionGroupService extends LanguageBoundService<TestQuestionGroup, TestQuestionGroupRepository> {

    /**
     * @return the groups of the test of the current user in the order of the test
     */
    List<TestQuestionGroup> findByTest(Test test) throws Exception;

    /**
     * Deletes the groups of the test (used when the test is deleted), without their questions: delete those with
     * {@link TestQuestionService#deleteForTest} first.
     */
    int deleteForTest(Test test, boolean hard) throws Exception;

}
