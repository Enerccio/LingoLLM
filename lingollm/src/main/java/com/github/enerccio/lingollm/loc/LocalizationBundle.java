package com.github.enerccio.lingollm.loc;

import java.text.DateFormat;
import java.util.Locale;
import java.util.Map;

/**
 * The texts and formats of one language. The application ships {@link LocalizationBundleEN}, an extension adds a
 * language by implementing {@link com.github.enerccio.lingollm.extensions.ExtensionHasLocalization}. Which bundle is
 * used is decided by {@link Localization} from the language of the user's session.
 * <p>
 * A bundle doesn't need to have every key: a text that is missing is taken from the default language. Extend
 * {@link LocalizationBundleBase} to fill the texts with {@code setValue}.
 */
public interface LocalizationBundle {

    Language getLanguage();

    /**
     * Name of the language in the language itself ("English", "Čeština"), shown in the language selector.
     */
    String getDisplayName();

    Locale getLocale();

    DateFormat getDateFormat();

    DateFormat getHourFormat();

    DateFormat getDateHourFormat();

    /**
     * The text of the key, {@code null} when the bundle doesn't have it.
     */
    String getValue(L l);

    /**
     * All texts of the bundle.
     */
    Map<L, String> getMessages();

}
