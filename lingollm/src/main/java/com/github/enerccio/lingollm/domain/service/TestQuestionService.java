package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.domain.repository.TestQuestionRepository;

import java.util.List;

public interface TestQuestionService extends LanguageBoundService<TestQuestion, TestQuestionRepository> {

    /**
     * @return the questions of the test of the current user in the order of the test (group first, then question)
     */
    List<TestQuestion> findByTest(Test test) throws Exception;

    /**
     * Deletes the questions of the test (used when the test is deleted), with their answers.
     */
    int deleteForTest(Test test, boolean hard) throws Exception;

}
