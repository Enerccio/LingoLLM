package com.github.enerccio.lingollm.domain;

import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.collections.TestType;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionResult;
import com.github.enerccio.lingollm.domain.model.impl.TestResult;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.github.enerccio.lingollm.domain.service.TestQuestionResultService;
import com.github.enerccio.lingollm.domain.service.TestQuestionService;
import com.github.enerccio.lingollm.domain.service.TestResultService;
import com.github.enerccio.lingollm.domain.service.TestService;
import com.github.enerccio.lingollm.test.LingoLLMTestBase;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** An attempt at a test: the result is made at the start, the answers are recorded one by one, the attempt is finished. */
class TestAttemptTest extends LingoLLMTestBase {

    @Autowired
    private LanguageService languageService;

    @Autowired
    private TestService testService;

    @Autowired
    private TestQuestionService questionService;

    @Autowired
    private TestResultService resultService;

    @Autowired
    private TestQuestionResultService answerService;

    private Language language;

    private Test test(TestType type) throws Exception {
        Test test = new Test();
        test.setLanguage(language);
        test.setType(type);
        return testService.save(test);
    }

    private TestQuestion question(Test test, int ordinal) throws Exception {
        TestQuestion question = new TestQuestion();
        question.setLanguage(language);
        question.setParentTest(test);
        question.setOrdinal(ordinal);
        return questionService.save(question);
    }

    private void profile() throws Exception {
        login();
        Language l = new Language();
        l.setLanguage(Languages.GERMAN);
        language = languageService.save(l);
    }

    @org.junit.jupiter.api.Test
    void aTestIsRegularUnlessSaidOtherwise() throws Exception {
        profile();
        Test test = new Test();
        test.setLanguage(language);

        assertThat(testService.save(test).getType()).isEqualTo(TestType.REGULAR);
        assertThat(test(TestType.GRADE).getType()).isEqualTo(TestType.GRADE);
        test.setType(null);
        assertThat(test.getType()).isEqualTo(TestType.REGULAR);
    }

    @org.junit.jupiter.api.Test
    void startingCreatesTheUnfinishedResultAtOnce() throws Exception {
        profile();
        Test grade = test(TestType.GRADE);
        assertThat(resultService.findUnfinished(language, TestType.GRADE)).isNull();

        TestResult result = resultService.start(grade);

        assertThat(result.getId()).isNotNull();
        assertThat(result.isFinished()).isFalse();
        assertThat(result.getTest().getId()).isEqualTo(grade.getId());
        assertThat(result.getLanguage().getId()).isEqualTo(language.getId());
        TestResult found = resultService.findUnfinished(language, TestType.GRADE);
        assertThat(found.getId()).isEqualTo(result.getId());
        // the other kind of tests has none
        assertThat(resultService.findUnfinished(language, TestType.REGULAR)).isNull();

        result.setFinished(true);
        resultService.save(result);
        assertThat(resultService.findUnfinished(language, TestType.GRADE)).isNull();
    }

    @org.junit.jupiter.api.Test
    void anUnfinishedResultIsOfTheLanguageAndTheUser() throws Exception {
        profile();
        resultService.start(test(TestType.GRADE));
        Language other = new Language();
        other.setLanguage(Languages.JAPANESE);
        other = languageService.save(other);

        assertThat(resultService.findUnfinished(other, TestType.GRADE)).isNull();
        assertThat(resultService.findUnfinished(null, TestType.GRADE)).isNull();

        loginAs(createUser());
        assertThat(resultService.findUnfinished(language, TestType.GRADE)).isNull();
    }

    @org.junit.jupiter.api.Test
    void theNewestUnfinishedResultIsFound() throws Exception {
        profile();
        resultService.start(test(TestType.GRADE));
        TestResult newer = resultService.start(test(TestType.GRADE));

        assertThat(resultService.findUnfinished(language, TestType.GRADE).getId()).isEqualTo(newer.getId());
    }

    @org.junit.jupiter.api.Test
    void anAnswerIsRecordedOnceAndRewrittenWhenTheLearnerGoesBack() throws Exception {
        profile();
        Test grade = test(TestType.GRADE);
        TestQuestion first = question(grade, 0);
        TestQuestion second = question(grade, 1);
        TestResult result = resultService.start(grade);

        answerService.record(result, first, "1", false);
        answerService.record(result, second, null, true);
        assertThat(answerService.findByTestResult(result)).hasSize(2);

        // back to the first: the answer is replaced, not added
        TestQuestionResult rewritten = answerService.record(result, first, "2", false);
        List<TestQuestionResult> answers = answerService.findByTestResult(result);
        assertThat(answers).hasSize(2);
        assertThat(rewritten.getId()).isEqualTo(answers.getFirst().getId());
        assertThat(answers.getFirst().getAnswer()).isEqualTo("2");
        assertThat(answers.getFirst().isDontKnow()).isFalse();
        assertThat(answers.getFirst().getQuestion().getId()).isEqualTo(first.getId());
        assertThat(answers.getFirst().getLanguage().getId()).isEqualTo(language.getId());

        // don't know drops the answer, an answer drops don't know
        answerService.record(result, first, "3", true);
        assertThat(answerService.findByTestResult(result).getFirst().getAnswer()).isNull();
        assertThat(answerService.findByTestResult(result).getFirst().isDontKnow()).isTrue();
        answerService.record(result, second, "0", false);
        assertThat(answerService.findByTestResult(result).get(1).getAnswer()).isEqualTo("0");
        assertThat(answerService.findByTestResult(result).get(1).isDontKnow()).isFalse();
    }

    @org.junit.jupiter.api.Test
    void anAnswerNeedsAnAttemptAndAQuestion() throws Exception {
        profile();
        Test grade = test(TestType.GRADE);
        TestResult result = resultService.start(grade);

        assertThatThrownBy(() -> answerService.record(null, question(grade, 0), "1", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> answerService.record(result, null, "1", false)).isInstanceOf(IllegalArgumentException.class);
    }

    @org.junit.jupiter.api.Test
    void discardingTheTestDeletesTheAttemptAndItsAnswers() throws Exception {
        profile();
        Test grade = test(TestType.GRADE);
        TestResult result = resultService.start(grade);
        answerService.record(result, question(grade, 0), "1", false);

        testService.delete(testService.find(grade), true);

        assertThat(resultService.findUnfinished(language, TestType.GRADE)).isNull();
        assertThat(testService.findAllForUser()).isEmpty();
        assertThat(resultService.findAllForUser()).isEmpty();
        assertThat(answerService.findAllForUser()).isEmpty();
    }
}
