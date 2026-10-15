package com.github.enerccio.lingollm.loc;

import com.github.enerccio.lingollm.bound.SessionPoint;
import com.github.enerccio.lingollm.domain.collections.CEFR;
import com.github.enerccio.lingollm.domain.model.impl.settings.UserSetting;
import com.github.enerccio.lingollm.domain.service.SettingService;
import com.github.enerccio.lingollm.test.LingoLLMTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.SimpleDateFormat;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class LocalizationTest extends LingoLLMTestBase {

    private static final Language CS = Language.of("cs");

    @Autowired
    private Localization loc;

    @Autowired
    private SessionPoint sessionPoint;

    @Autowired
    private SettingService settingService;

    /** Czech bundle with a single text, the others have to come from the default language. */
    private static class PartialBundle extends LocalizationBundleBase {
        private final Language language;

        PartialBundle(Language language) {
            this.language = language;
        }

        @Override
        protected void loadMessages() {
            setValue(L.LABEL_YES, "Ano");
        }

        @Override
        public Language getLanguage() {
            return language;
        }

        @Override
        public String getDisplayName() {
            return "Čeština";
        }

        @Override
        public Locale getLocale() {
            return Locale.of("cs", "CZ");
        }

        @Override
        public SimpleDateFormat getDateFormat() {
            return new SimpleDateFormat("dd.MM.yyyy");
        }

        @Override
        public SimpleDateFormat getHourFormat() {
            return new SimpleDateFormat("HH:mm:ss");
        }

        @Override
        public SimpleDateFormat getDateHourFormat() {
            return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        }
    }

    @Test
    void sessionWithoutLanguageUsesDefault() {
        assertEquals(Language.EN, loc.getDefaultLanguage());
        assertEquals(Language.EN, loc.getCurrentLanguage());
        assertEquals("Yes", loc.getValue(L.LABEL_YES));
        assertTrue(loc.getAvailableLanguages().contains(Language.EN));
        assertEquals("English", loc.getDisplayName(Language.EN));
    }

    @Test
    void sessionLanguageSelectsBundleAndMissingTextsFallBack() {
        PartialBundle bundle = new PartialBundle(CS);
        loc.registerBundle(bundle);
        try {
            assertTrue(loc.getAvailableLanguages().contains(CS));
            assertEquals(CS, loc.resolveLanguage("CS"));

            sessionPoint.setLanguage(CS);
            assertEquals(CS, loc.getCurrentLanguage());
            assertEquals("Ano", loc.getValue(L.LABEL_YES));
            assertEquals("No", loc.getValue(L.LABEL_NO));
            assertEquals("cs", loc.getLocale().getLanguage());
            assertEquals(L.LABEL_YES, loc.parseValue("Ano"));
        } finally {
            loc.unregisterBundle(bundle);
            sessionPoint.setLanguage(null);
        }
    }

    @Test
    void removedBundleFallsBackToDefault() {
        PartialBundle bundle = new PartialBundle(CS);
        loc.registerBundle(bundle);
        sessionPoint.setLanguage(CS);
        loc.unregisterBundle(bundle);
        try {
            assertFalse(loc.getAvailableLanguages().contains(CS));
            assertEquals(Language.EN, loc.getCurrentLanguage());
            assertEquals("Yes", loc.getValue(L.LABEL_YES));
            assertEquals(Language.EN, loc.resolveLanguage("cs"));
        } finally {
            sessionPoint.setLanguage(null);
        }
    }

    @Test
    void languageWithBundleCantBeRegisteredTwice() {
        assertThrows(IllegalArgumentException.class, () -> loc.registerBundle(new PartialBundle(Language.EN)));

        PartialBundle bundle = new PartialBundle(CS);
        loc.registerBundle(bundle);
        try {
            assertThrows(IllegalArgumentException.class, () -> loc.registerBundle(new PartialBundle(CS)));
        } finally {
            loc.unregisterBundle(bundle);
        }
    }

    @Test
    void defaultLanguageCantBeUnregistered() {
        loc.unregisterBundle(new LocalizationBundleEN());
        assertEquals("Yes", loc.getValue(L.LABEL_YES));
        assertTrue(loc.getAvailableLanguages().contains(Language.EN));
    }

    @Test
    void userLanguageIsSaved() throws Exception {
        login();
        UserSetting setting = settingService.getOrCreate(UserSetting.class);
        assertNull(setting.getLanguage());
        setting.setLanguage("cs");
        settingService.save(setting);

        assertEquals("cs", settingService.getOrCreate(UserSetting.class).getLanguage());
    }

    @Test
    void allKeysHaveEnglishText() {
        LocalizationBundleEN en = new LocalizationBundleEN();
        for (L key : L.values()) {
            assertNotNull(en.getValue(key), key.name());
        }
    }

    @Test
    void everyCefrLevelHasALocalizedName() {
        for (CEFR level : CEFR.values()) {
            L key = loc.getCefrLevel(level);
            assertNotNull(key, level.name());
            assertTrue(loc.getValue(key).startsWith(level.name()), level.name());
        }
    }
}
