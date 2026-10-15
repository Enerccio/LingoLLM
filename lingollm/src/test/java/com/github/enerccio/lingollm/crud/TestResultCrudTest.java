package com.github.enerccio.lingollm.crud;

import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestResult;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.github.enerccio.lingollm.domain.service.OwnedService;
import com.github.enerccio.lingollm.domain.service.TestResultService;
import com.github.enerccio.lingollm.domain.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class TestResultCrudTest extends ExtendableCrudContract<TestResult> {

    @Autowired
    private TestResultService resultService;

    @Autowired
    private TestService testService;

    @Autowired
    private LanguageService languageService;

    @Override
    protected OwnedService<TestResult, ?> service() {
        return resultService;
    }

    @Override
    protected TestResult newEntity() throws Exception {
        Language language = new Language();
        language.setLanguage(Languages.GERMAN);
        Test test = new Test();
        test.setLanguage(languageService.save(language));
        test = testService.save(test);
        TestResult result = new TestResult();
        result.setLanguage(test.getLanguage());
        result.setTest(test);
        result.setScore(3);
        result.setMaxScore(5);
        return result;
    }

    @Override
    protected void assertCreated(TestResult loaded) {
        assertThat(loaded.getScore()).isEqualTo(3);
        assertThat(loaded.getMaxScore()).isEqualTo(5);
        assertThat(loaded.getTest()).isNotNull();
    }

    @Override
    protected void modify(TestResult entity) {
        entity.setScore(4);
    }

    @Override
    protected void assertModified(TestResult loaded) {
        assertThat(loaded.getScore()).isEqualTo(4);
        assertThat(loaded.getMaxScore()).isEqualTo(5);
    }

    @org.junit.jupiter.api.Test
    void attemptsAreListedPerTest() throws Exception {
        TestResult first = create();
        TestResult second = new TestResult();
        second.setLanguage(first.getLanguage());
        second.setTest(first.getTest());
        second = resultService.save(second);

        assertThat(resultService.findByTest(first.getTest())).extracting(TestResult::getId)
                .containsExactly(first.getId(), second.getId());
    }
}
