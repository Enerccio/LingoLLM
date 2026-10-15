package com.github.enerccio.lingollm.crud;

import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.github.enerccio.lingollm.domain.service.OwnedService;
import com.github.enerccio.lingollm.domain.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class TestCrudTest extends ExtendableCrudContract<Test> {

    @Autowired
    private TestService testService;

    @Autowired
    private LanguageService languageService;

    @Override
    protected OwnedService<Test, ?> service() {
        return testService;
    }

    @Override
    protected Test newEntity() throws Exception {
        Language language = new Language();
        language.setLanguage(Languages.GERMAN);
        Test test = new Test();
        test.setLanguage(languageService.save(language));
        test.setName("Articles");
        test.setDescription("der, die, das");
        return test;
    }

    @Override
    protected void assertCreated(Test loaded) {
        assertThat(loaded.getName()).isEqualTo("Articles");
        assertThat(loaded.getDescription()).isEqualTo("der, die, das");
        assertThat(loaded.getLanguage()).isNotNull();
    }

    @Override
    protected void modify(Test entity) {
        entity.setName("Cases");
    }

    @Override
    protected void assertModified(Test loaded) {
        assertThat(loaded.getName()).isEqualTo("Cases");
        assertThat(loaded.getDescription()).isEqualTo("der, die, das");
    }
}
