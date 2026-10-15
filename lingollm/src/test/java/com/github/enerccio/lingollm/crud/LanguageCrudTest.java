package com.github.enerccio.lingollm.crud;

import com.github.enerccio.lingollm.domain.collections.CEFR;
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
        language.setUserGrade(CEFR.A2);
        language.setUserGradeFineDetails("reads hiragana only");
        language.getLanguageSpecificData().addProperty("kana", "both");
        return language;
    }

    @Override
    protected void assertCreated(Language loaded) {
        assertThat(loaded.getLanguage()).isEqualTo(Languages.JAPANESE);
        assertThat(loaded.getNameOverride()).isEqualTo("Nihongo");
        assertThat(loaded.getNote()).isEqualTo("evening class");
        assertThat(loaded.getUserGrade()).isEqualTo(CEFR.A2);
        assertThat(loaded.getUserGradeFineDetails()).isEqualTo("reads hiragana only");
        assertThat(loaded.getLanguageSpecificData().get("kana").getAsString()).isEqualTo("both");
    }

    @Override
    protected void modify(Language entity) {
        entity.setNote("changed");
        entity.setUserGrade(CEFR.B1);
        entity.setUserGradeFineDetails("katakana too");
        entity.getLanguageSpecificData().addProperty("kana", "hiragana");
    }

    @Override
    protected void assertModified(Language loaded) {
        assertThat(loaded.getNote()).isEqualTo("changed");
        assertThat(loaded.getUserGrade()).isEqualTo(CEFR.B1);
        assertThat(loaded.getUserGradeFineDetails()).isEqualTo("katakana too");
        assertThat(loaded.getLanguageSpecificData().get("kana").getAsString()).isEqualTo("hiragana");
        assertThat(loaded.getNameOverride()).isEqualTo("Nihongo");
    }

    @Test
    void gaugeTestPromptIsTheOneOfTheProfileWhenSetAndTheDefaultOfTheLanguageOtherwise() throws Exception {
        Language profile = create();
        assertThat(languageService.getGaugeTestPrompt(profile)).isEqualTo(Languages.JAPANESE.defaults.getGaugeTestPrompt());
        assertThat(languageService.getGaugeTestPrompt(profile)).contains("{{questions}}");

        profile.getDefaults().setGaugeTestPrompt("My own {{language}} test");
        Language saved = languageService.find(languageService.save(profile));
        assertThat(saved.getDefaults().getGaugeTestPrompt()).isEqualTo("My own {{language}} test");
        assertThat(languageService.getGaugeTestPrompt(saved)).isEqualTo("My own {{language}} test");

        saved.getDefaults().setGaugeTestPrompt("  ");
        assertThat(languageService.getGaugeTestPrompt(saved)).isEqualTo(Languages.JAPANESE.defaults.getGaugeTestPrompt());
    }

    @Test
    void everyLevelHasADescriptionForTheModel() {
        for (CEFR level : CEFR.values()) {
            assertThat(level.getDescription()).isNotBlank();
        }
        assertThat(CEFR.values()).extracting(Enum::name).containsExactly("A1", "A2", "B1", "B2", "C1", "C2");
    }

    @Test
    void profilesBelongToTheirUser() throws Exception {
        Language mine = create();
        assertThat(languageService.findAllForUser()).extracting(Language::getId).contains(mine.getId());

        login();
        assertThat(languageService.findAllForUser()).extracting(Language::getId).doesNotContain(mine.getId());
    }
}
