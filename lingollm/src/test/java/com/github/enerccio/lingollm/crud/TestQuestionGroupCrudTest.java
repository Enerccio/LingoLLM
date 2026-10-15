package com.github.enerccio.lingollm.crud;

import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionGroup;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.github.enerccio.lingollm.domain.service.OwnedService;
import com.github.enerccio.lingollm.domain.service.TestQuestionGroupService;
import com.github.enerccio.lingollm.domain.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestQuestionGroupCrudTest extends ExtendableCrudContract<TestQuestionGroup> {

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

    private TestQuestionGroup group(Test test, int ordinal) throws Exception {
        TestQuestionGroup group = new TestQuestionGroup();
        group.setLanguage(test.getLanguage());
        group.setParentTest(test);
        group.setOrdinal(ordinal);
        return groupService.save(group);
    }

    @Override
    protected OwnedService<TestQuestionGroup, ?> service() {
        return groupService;
    }

    @Override
    protected TestQuestionGroup newEntity() throws Exception {
        Test test = test();
        TestQuestionGroup group = new TestQuestionGroup();
        group.setLanguage(test.getLanguage());
        group.setParentTest(test);
        group.setOrdinal(1);
        group.setFactory("some.Factory");
        return group;
    }

    @Override
    protected void assertCreated(TestQuestionGroup loaded) {
        assertThat(loaded.getOrdinal()).isEqualTo(1);
        assertThat(loaded.getFactory()).isEqualTo("some.Factory");
        assertThat(loaded.getParentTest()).isNotNull();
    }

    @Override
    protected void modify(TestQuestionGroup entity) {
        entity.setFactory("other.Factory");
    }

    @Override
    protected void assertModified(TestQuestionGroup loaded) {
        assertThat(loaded.getFactory()).isEqualTo("other.Factory");
        assertThat(loaded.getOrdinal()).isEqualTo(1);
    }

    @org.junit.jupiter.api.Test
    void groupsAreListedInTheOrderOfTheTest() throws Exception {
        Test test = test();
        TestQuestionGroup second = group(test, 2);
        TestQuestionGroup first = group(test, 1);

        assertThat(groupService.findByTest(test)).extracting(TestQuestionGroup::getId)
                .containsExactly(first.getId(), second.getId());
    }

    @org.junit.jupiter.api.Test
    void groupsOfATestOfSomebodyElseAreRefused() throws Exception {
        Test test = test();
        login();

        assertThatThrownBy(() -> groupService.findByTest(test)).isInstanceOf(IllegalArgumentException.class);
    }
}
