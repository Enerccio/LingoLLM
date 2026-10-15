package com.github.enerccio.lingollm.ui.lang.languages.japanese;

import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageStringBuilder;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageStringMetadata;

import java.util.Comparator;
import java.util.List;

/**
 * Furigana, the reading of a part of a Japanese {@link LanguageString}: the metadata of the type {@link #TYPE} covers
 * the text (usually kanji) and its value is the reading, kana as a rule. The reading is not checked: a model may
 * answer with anything (latin letters, ...) and that is better kept than lost.
 */
public final class Furigana {

    public static final String TYPE = "furigana";

    private Furigana() {
    }

    /**
     * Adds furigana over {@code [start, end)} of the text of the builder.
     *
     * @throws IllegalArgumentException when the part is not inside the text or there is no reading
     */
    public static LanguageStringBuilder annotate(LanguageStringBuilder builder, int start, int end, String kana) {
        return builder.annotate(TYPE, start, end, kana);
    }

    /**
     * Appends text with its furigana.
     *
     * @throws IllegalArgumentException when the text is empty or there is no reading
     */
    public static LanguageStringBuilder append(LanguageStringBuilder builder, String text, String kana) {
        return builder.append(text, TYPE, kana);
    }

    /**
     * @return the furigana of the string ordered by their position
     */
    public static List<LanguageStringMetadata> of(LanguageString string) {
        return string.getMetadata(TYPE).stream()
                .sorted(Comparator.comparingInt(LanguageStringMetadata::getStart).thenComparingInt(LanguageStringMetadata::getEnd))
                .toList();
    }
}
