package com.github.enerccio.lingollm.crud;

import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.collections.GradedBy;
import com.github.enerccio.lingollm.domain.collections.TestVerdict;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionResult;
import com.github.enerccio.lingollm.domain.model.impl.TestResult;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.github.enerccio.lingollm.domain.service.OwnedService;
import com.github.enerccio.lingollm.domain.service.TestQuestionResultService;
import com.github.enerccio.lingollm.domain.service.TestQuestionService;
import com.github.enerccio.lingollm.domain.service.TestResultService;
import com.github.enerccio.lingollm.domain.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class TestQuestionResultCrudTest extends ExtendableCrudContract<TestQuestionResult> {

    @Autowired
    private TestQuestionResultService answerService;

    @Autowired
    private TestResultService resultService;

    @Autowired
    private TestQuestionService questionService;

    @Autowired
    private TestService testService;

    @Autowired
    private LanguageService languageService;

    @Override
    protected OwnedService<TestQuestionResult, ?> service() {
        return answerService;
    }

    @Override
    protected TestQuestionResult newEntity() throws Exception {
        Language language = new Language();
        language.setLanguage(Languages.GERMAN);
        language = languageService.save(language);
        Test test = new Test();
        test.setLanguage(language);
        test = testService.save(test);
        TestQuestion question = new TestQuestion();
        question.setLanguage(language);
        question.setParentTest(test);
        question = questionService.save(question);
        TestResult result = new TestResult();
        result.setLanguage(language);
        result.setTest(test);
        result = resultService.save(result);

        TestQuestionResult answer = new TestQuestionResult();
        answer.setLanguage(language);
        answer.setTestResult(result);
        answer.setQuestion(question);
        answer.setAnswer("der Hund");
        answer.setVerdict(TestVerdict.PARTIAL);
        answer.setGradedBy(GradedBy.LLM);
        answer.setFeedback("the article is wrong");
        answer.setDontKnow(true);
        return answer;
    }

    @Override
    protected void assertCreated(TestQuestionResult loaded) {
        assertThat(loaded.getAnswer()).isEqualTo("der Hund");
        assertThat(loaded.getVerdict()).isEqualTo(TestVerdict.PARTIAL);
        assertThat(loaded.getGradedBy()).isEqualTo(GradedBy.LLM);
        assertThat(loaded.getFeedback()).isEqualTo("the article is wrong");
        assertThat(loaded.isDontKnow()).isTrue();
        assertThat(loaded.getTestResult()).isNotNull();
        assertThat(loaded.getQuestion()).isNotNull();
    }

    @Override
    protected void modify(TestQuestionResult entity) {
        entity.setVerdict(TestVerdict.CORRECT);
        entity.setGradedBy(GradedBy.SERVER);
    }

    @Override
    protected void assertModified(TestQuestionResult loaded) {
        assertThat(loaded.getVerdict()).isEqualTo(TestVerdict.CORRECT);
        assertThat(loaded.getGradedBy()).isEqualTo(GradedBy.SERVER);
        assertThat(loaded.getAnswer()).isEqualTo("der Hund");
    }

    @org.junit.jupiter.api.Test
    void answersAreListedPerAttempt() throws Exception {
        TestQuestionResult answer = create();

        assertThat(answerService.findByTestResult(answer.getTestResult())).extracting(TestQuestionResult::getId)
                .containsExactly(answer.getId());
    }
}
