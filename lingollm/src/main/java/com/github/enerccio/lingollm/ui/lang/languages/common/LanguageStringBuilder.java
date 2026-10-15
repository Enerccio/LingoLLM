package com.github.enerccio.lingollm.ui.lang.languages.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@link LanguageString}. The text is appended in pieces, a piece can carry metadata that covers it, or
 * metadata can be added over any part of the text appended so far. Any number of metadata, of any kinds, can be added,
 * they may overlap. Not thread safe.
 */
public final class LanguageStringBuilder {

    private final StringBuilder text = new StringBuilder();
    private final List<LanguageStringMetadata> metadata = new ArrayList<>();

    LanguageStringBuilder() {
    }

    /**
     * Appends text without metadata.
     */
    public LanguageStringBuilder append(String string) {
        if (string != null) {
            text.append(string);
        }
        return this;
    }

    /**
     * Appends text and covers it with metadata.
     */
    public LanguageStringBuilder append(String string, String type, String value) {
        if (string == null || string.isEmpty()) {
            throw new IllegalArgumentException("Nothing to put the metadata on");
        }
        int start = text.length();
        text.append(string);
        return annotate(type, start, text.length(), value);
    }

    /**
     * Adds metadata over {@code [start, end)} of the text appended so far.
     *
     * @param type  the kind of the metadata, who understands the value
     * @param value what the kind needs, not interpreted here
     * @throws IllegalArgumentException when the part is empty or not inside the text, or the kind or value is missing
     */
    public LanguageStringBuilder annotate(String type, int start, int end, String value) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Metadata needs a type");
        }
        if (value == null) {
            throw new IllegalArgumentException("Metadata needs a value");
        }
        if (start < 0 || end <= start || end > text.length()) {
            throw new IllegalArgumentException("Metadata [" + start + ", " + end + ") is not a part of the text of length "
                    + text.length());
        }
        metadata.add(new LanguageStringMetadata(type, start, end, value));
        return this;
    }

    /**
     * @return the string, the builder can go on and make other strings
     */
    public LanguageString build() {
        return new LanguageString(text.toString(), metadata);
    }
}
