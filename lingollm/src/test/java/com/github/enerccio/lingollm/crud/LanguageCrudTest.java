package com.github.enerccio.lingollm.crud;

import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.github.enerccio.lingollm.domain.service.OwnedService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageCrudTest extends ExtendableCrudContract<Language> {

    @Autowired
    private LanguageService languageService;

    @Override
    protected OwnedService<Language, ?> service() {
        return languageService;
    }

    @Override
    protected Language newEntity() {
        Language language = new Language();
        language.setLanguage(Languages.JAPANESE);
        language.setNameOverride("Nihongo");
        language.setNote("evening class");
        language.getLanguageSpecificData().addProperty("kana", "both");
        return language;
    }

    @Override
    protected void assertCreated(Language loaded) {
        assertThat(loaded.getLanguage()).isEqualTo(Languages.JAPANESE);
        assertThat(loaded.getNameOverride()).isEqualTo("Nihongo");
        assertThat(loaded.getNote()).isEqualTo("evening class");
        assertThat(loaded.getLanguageSpecificData().get("kana").getAsString()).isEqualTo("both");
    }

    @Override
    protected void modify(Language entity) {
        entity.setNote("changed");
        entity.getLanguageSpecificData().addProperty("kana", "hiragana");
    }

    @Override
    protected void assertModified(Language loaded) {
        assertThat(loaded.getNote()).isEqualTo("changed");
        assertThat(loaded.getLanguageSpecificData().get("kana").getAsString()).isEqualTo("hiragana");
        assertThat(loaded.getNameOverride()).isEqualTo("Nihongo");
    }

    @Test
    void profilesBelongToTheirUser() throws Exception {
        Language mine = create();
        assertThat(languageService.findAllForUser()).extracting(Language::getId).contains(mine.getId());

        login();
        assertThat(languageService.findAllForUser()).extracting(Language::getId).doesNotContain(mine.getId());
    }
}
