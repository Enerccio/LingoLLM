package com.github.enerccio.lingollm.crud;

import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.model.BaseEntity;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionGroup;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionResult;
import com.github.enerccio.lingollm.domain.model.impl.TestResult;
import com.github.enerccio.lingollm.domain.service.BaseService;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.github.enerccio.lingollm.domain.service.TestQuestionGroupService;
import com.github.enerccio.lingollm.domain.service.TestQuestionResultService;
import com.github.enerccio.lingollm.domain.service.TestQuestionService;
import com.github.enerccio.lingollm.domain.service.TestResultService;
import com.github.enerccio.lingollm.domain.service.TestService;
import com.github.enerccio.lingollm.test.LingoLLMTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deleting a test takes its groups, questions, attempts and answers with it, deleting an attempt takes its answers.
 */
class TestCascadeTest extends LingoLLMTestBase {

    @Autowired
    private TestService testService;

    @Autowired
    private TestQuestionService questionService;

    @Autowired
    private TestQuestionGroupService groupService;

    @Autowired
    private TestResultService resultService;

    @Autowired
    private TestQuestionResultService answerService;

    @Autowired
    private LanguageService languageService;

    private Language language;

    @BeforeEach
    void profile() throws Exception {
        login();
        Language l = new Language();
        l.setLanguage(Languages.GERMAN);
        language = languageService.save(l);
    }

    /**
     * A test with two questions and two attempts that answered both.
     */
    private class Fixture {
        final Test test;
        final TestQuestionGroup group;
        final TestQuestion[] questions = new TestQuestion[2];
        final TestResult[] results = new TestResult[2];
        final TestQuestionResult[][] answers = new TestQuestionResult[2][2];

        Fixture() throws Exception {
            Test t = new Test();
            t.setLanguage(language);
            test = testService.save(t);
            TestQuestionGroup g = new TestQuestionGroup();
            g.setLanguage(language);
            g.setParentTest(test);
            group = groupService.save(g);
            for (int q = 0; q < 2; q++) {
                TestQuestion question = new TestQuestion();
                question.setLanguage(language);
                question.setParentTest(test);
                question.setQuestionGroup(group);
                question.setOrdinal(q);
                questions[q] = questionService.save(question);
            }
            for (int r = 0; r < 2; r++) {
                TestResult result = new TestResult();
                result.setLanguage(language);
                result.setTest(test);
                results[r] = resultService.save(result);
                for (int q = 0; q < 2; q++) {
                    TestQuestionResult answer = new TestQuestionResult();
                    answer.setLanguage(language);
                    answer.setTestResult(results[r]);
                    answer.setQuestion(questions[q]);
                    answers[r][q] = answerService.save(answer);
                }
            }
        }
    }

    private void assertSoftDeleted(BaseService<?, ?> service, BaseEntity entity) throws Exception {
        assertThat(service.findAllIds()).doesNotContain(entity.getId());
        assertThat(service.find(entity.getUuid())).isNotNull();
    }

    private void assertGone(BaseService<?, ?> service, BaseEntity entity) throws Exception {
        assertThat(service.find(entity.getUuid())).isNull();
    }

    private void assertAlive(BaseService<?, ?> service, BaseEntity entity) throws Exception {
        assertThat(service.findAllIds()).contains(entity.getId());
    }

    @org.junit.jupiter.api.Test
    void softDeletingATestSoftDeletesEverythingBelow() throws Exception {
        Fixture f = new Fixture();
        Fixture untouched = new Fixture();

        testService.delete(f.test, false);

        assertSoftDeleted(testService, f.test);
        assertSoftDeleted(groupService, f.group);
        for (int i = 0; i < 2; i++) {
            assertSoftDeleted(questionService, f.questions[i]);
            assertSoftDeleted(resultService, f.results[i]);
            for (int j = 0; j < 2; j++) {
                assertSoftDeleted(answerService, f.answers[i][j]);
            }
        }
        assertAlive(testService, untouched.test);
        assertAlive(groupService, untouched.group);
        assertAlive(questionService, untouched.questions[0]);
        assertAlive(resultService, untouched.results[0]);
        assertAlive(answerService, untouched.answers[0][0]);
    }

    @org.junit.jupiter.api.Test
    void hardDeletingATestDeletesEverythingBelow() throws Exception {
        Fixture f = new Fixture();
        Fixture untouched = new Fixture();

        testService.delete(f.test, true);

        assertGone(testService, f.test);
        assertGone(groupService, f.group);
        for (int i = 0; i < 2; i++) {
            assertGone(questionService, f.questions[i]);
            assertGone(resultService, f.results[i]);
            for (int j = 0; j < 2; j++) {
                assertGone(answerService, f.answers[i][j]);
            }
        }
        assertAlive(testService, untouched.test);
        assertAlive(groupService, untouched.group);
        assertAlive(questionService, untouched.questions[0]);
        assertAlive(resultService, untouched.results[0]);
        assertAlive(answerService, untouched.answers[0][0]);
    }

    @org.junit.jupiter.api.Test
    void deletingAnAttemptDeletesItsAnswersOnly() throws Exception {
        Fixture f = new Fixture();

        resultService.delete(f.results[0], false);

        assertSoftDeleted(resultService, f.results[0]);
        assertSoftDeleted(answerService, f.answers[0][0]);
        assertSoftDeleted(answerService, f.answers[0][1]);
        assertAlive(resultService, f.results[1]);
        assertAlive(answerService, f.answers[1][0]);
        assertAlive(questionService, f.questions[0]);

        resultService.delete(f.results[1], true);

        assertGone(resultService, f.results[1]);
        assertGone(answerService, f.answers[1][0]);
        assertGone(answerService, f.answers[1][1]);
        assertAlive(questionService, f.questions[0]);
        assertAlive(testService, f.test);
    }

    @org.junit.jupiter.api.Test
    void softDeletingTheLanguageSoftDeletesTheTestsToo() throws Exception {
        Fixture f = new Fixture();

        languageService.delete(language, false);

        assertSoftDeleted(testService, f.test);
        assertSoftDeleted(groupService, f.group);
        assertSoftDeleted(questionService, f.questions[0]);
        assertSoftDeleted(resultService, f.results[0]);
        assertSoftDeleted(answerService, f.answers[0][0]);
    }
}
