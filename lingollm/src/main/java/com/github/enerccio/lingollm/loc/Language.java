package com.github.enerccio.lingollm.loc;

import java.io.Serializable;
import java.util.Locale;

/**
 * A language of the UI, identified by its code (e.g. {@code en}, {@code cs}). It is not an enum because extensions can
 * install bundles of languages the application doesn't know. The language is only a key: the name shown to the user
 * and the texts come from the {@link LocalizationBundle} registered for it.
 */
public record Language(String code) implements Serializable {

    public static final Language EN = new Language("en");

    public Language {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Language code can't be blank");
        }
        code = code.trim().toLowerCase(Locale.ROOT);
    }

    public static Language of(String code) {
        return new Language(code);
    }

    @Override
    public String toString() {
        return code;
    }
}
