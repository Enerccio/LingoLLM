package com.github.enerccio.lingollm.crud;

import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionGroup;
import com.github.enerccio.lingollm.domain.service.TestQuestionGroupService;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.github.enerccio.lingollm.domain.service.OwnedService;
import com.github.enerccio.lingollm.domain.service.TestQuestionService;
import com.github.enerccio.lingollm.domain.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class TestQuestionCrudTest extends ExtendableCrudContract<TestQuestion> {

    @Autowired
    private TestQuestionService questionService;

    @Autowired
    private TestQuestionGroupService groupService;

    @Autowired
    private TestService testService;

    @Autowired
    private LanguageService languageService;

    private Test test() throws Exception {
        Language language = new Language();
        language.setLanguage(Languages.JAPANESE);
        Test test = new Test();
        test.setLanguage(languageService.save(language));
        return testService.save(test);
    }

    private TestQuestion question(Test test, int ordinal) throws Exception {
        TestQuestion question = new TestQuestion();
        question.setLanguage(test.getLanguage());
        question.setParentTest(test);
        question.setOrdinal(ordinal);
        return questionService.save(question);
    }

    @Override
    protected OwnedService<TestQuestion, ?> service() {
        return questionService;
    }

    @Override
    protected TestQuestion newEntity() throws Exception {
        Test test = test();
        TestQuestion question = new TestQuestion();
        question.setLanguage(test.getLanguage());
        question.setParentTest(test);
        question.setOrdinal(1);
        question.setTestQuestionFactory("some.Factory");
        return question;
    }

    @Override
    protected void assertCreated(TestQuestion loaded) {
        assertThat(loaded.getOrdinal()).isEqualTo(1);
        assertThat(loaded.getTestQuestionFactory()).isEqualTo("some.Factory");
        assertThat(loaded.getParentTest()).isNotNull();
    }

    @Override
    protected void modify(TestQuestion entity) {
        entity.setOrdinal(2);
    }

    @Override
    protected void assertModified(TestQuestion loaded) {
        assertThat(loaded.getOrdinal()).isEqualTo(2);
        assertThat(loaded.getParentTest()).isNotNull();
    }

    @org.junit.jupiter.api.Test
    void questionsAreListedInTheOrderOfTheTest() throws Exception {
        Test test = test();
        TestQuestion second = question(test, 2);
        TestQuestion first = question(test, 1);

        assertThat(questionService.findByTest(test)).extracting(TestQuestion::getId).containsExactly(first.getId(), second.getId());
    }

    @org.junit.jupiter.api.Test
    void questionsAreListedByGroupThenByOrdinal() throws Exception {
        Test test = test();
        TestQuestionGroup later = group(test, 2);
        TestQuestionGroup earlier = group(test, 1);
        TestQuestion laterSecond = question(test, later, 2);
        TestQuestion laterFirst = question(test, later, 1);
        TestQuestion earlierOnly = question(test, earlier, 5);

        assertThat(questionService.findByTest(test)).extracting(TestQuestion::getId)
                .containsExactly(earlierOnly.getId(), laterFirst.getId(), laterSecond.getId());
        assertThat(questionService.findByTest(test).get(1).getQuestionGroup().getId()).isEqualTo(later.getId());
    }

    private TestQuestionGroup group(Test test, int ordinal) throws Exception {
        TestQuestionGroup group = new TestQuestionGroup();
        group.setLanguage(test.getLanguage());
        group.setParentTest(test);
        group.setOrdinal(ordinal);
        return groupService.save(group);
    }

    private TestQuestion question(Test test, TestQuestionGroup group, int ordinal) throws Exception {
        TestQuestion question = new TestQuestion();
        question.setLanguage(test.getLanguage());
        question.setParentTest(test);
        question.setQuestionGroup(group);
        question.setOrdinal(ordinal);
        return questionService.save(question);
    }

    @org.junit.jupiter.api.Test
    void questionsOfATestOfSomebodyElseAreRefused() throws Exception {
        Test test = test();
        login();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> questionService.findByTest(test))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
