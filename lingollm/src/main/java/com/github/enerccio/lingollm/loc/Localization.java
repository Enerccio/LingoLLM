package com.github.enerccio.lingollm.loc;

import com.github.enerccio.lingollm.domain.collections.*;
import com.github.enerccio.lingollm.domain.traits.CleanupReference;

import java.text.DateFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Texts and formats of the UI in the language of the current session ({@code SessionPoint}), the default language
 * when there is no session (startup, scheduled tasks). The texts come from {@link LocalizationBundle}s, one per
 * {@link Language}.
 */
public interface Localization {

    /**
     * The text in the language of the current session; a text its bundle doesn't have is taken from the default
     * language.
     */
    String getValue(L l);
    L parseValue(String text);
    Locale getLocale();
    DateFormat getDateFormat();
    DateFormat getHourFormat();
    DateFormat getDateHourFormat();
    Comparator<String> createNaturalLanguageComparator();
    Comparator<String> createLocaleComparator();

    L getAIType(AIType aiType);
    L getReasoningEffort(ReasoningEffort t);
    L getLimitStrategy(LimitStrategy t);
    L getResetStrategy(ResetStrategy t);
    L getCleanupPolicy(CleanupReference.Policy t);
    L getLanguageName(Languages language);
    L getCefrLevel(CEFR level);

    /**
     * Localized name of the kind of object an entity class stands for (language profile, chat...), subclasses use the name of
     * their closest known superclass.
     */
    String localizeDomainObject(Class<?> clazz);

    /**
     * Language that is used when the session has none, or has one without a bundle.
     */
    Language getDefaultLanguage();

    /**
     * Language of the current session, always one that has a bundle.
     */
    Language getCurrentLanguage();

    /**
     * Languages that have a bundle, sorted by their names.
     */
    List<Language> getAvailableLanguages();

    /**
     * Name of the language in the language itself, the code when it has no bundle.
     */
    String getDisplayName(Language language);

    /**
     * The language with the code when it has a bundle, otherwise the default language.
     */
    Language resolveLanguage(String code);

    /**
     * Adds a language.
     *
     * @throws IllegalArgumentException when the language already has a bundle
     */
    void registerBundle(LocalizationBundle bundle);

    /**
     * Removes the bundle if it is the registered one of its language. The default language can't be removed.
     */
    void unregisterBundle(LocalizationBundle bundle);
}
