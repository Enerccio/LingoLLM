package com.github.enerccio.lingollm.domain.service.impl;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.repository.TestRepository;
import com.github.enerccio.lingollm.domain.service.TestQuestionGroupService;
import com.github.enerccio.lingollm.domain.service.TestQuestionResultService;
import com.github.enerccio.lingollm.domain.service.TestQuestionService;
import com.github.enerccio.lingollm.domain.service.TestResultService;
import com.github.enerccio.lingollm.domain.service.TestService;
import com.github.enerccio.lingollm.domain.traits.CommonTx;
import org.springframework.beans.factory.annotation.Autowired;

public class TestServiceImpl extends LanguageBoundServiceImpl<Test, TestRepository> implements TestService {

    @Autowired
    private TestQuestionService questionService;

    @Autowired
    private TestQuestionGroupService groupService;

    @Autowired
    private TestResultService resultService;

    @Autowired
    private TestQuestionResultService questionResultService;

    /**
     * Deleting a test deletes (soft or hard, like the test) its questions, its groups, its attempts and their answers.
     */
    @Override
    @CommonTx
    public Test delete(Test test, boolean hard) throws Exception {
        if (test != null && test.getId() != null) {
            questionResultService.deleteForTest(test, hard);
            resultService.deleteForTest(test, hard);
            questionService.deleteForTest(test, hard);
            groupService.deleteForTest(test, hard);
        }
        return super.delete(test, hard);
    }

}
