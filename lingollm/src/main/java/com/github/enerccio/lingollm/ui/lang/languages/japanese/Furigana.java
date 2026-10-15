package com.github.enerccio.lingollm.ui.lang.languages.japanese;

import com.github.enerccio.lingollm.ui.components.LanguageStringComponent.AnnotationFilter;
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

    /**
     * A filter for {@code LanguageStringComponent} that hides the furigana over the text that contains the kanji (the kanji of the word, see {@link #kanjiCore}), so the
     * learner has to know its reading. A furigana entry covers a word (a run of kanji) as a whole, so the reading of
     * all of the word is hidden. Other annotations are shown.
     */
    public static AnnotationFilter hideOver(String kanji) {
        String core = kanjiCore(kanji);
        return (coveredText, metadata) -> !TYPE.equals(metadata.getType()) || core.isEmpty() || !coveredText.contains(core);
    }

    /**
     * The word from its first to its last kanji (the furigana does not cover the kana around it, 食べる has it over 食).
     * The word itself when it has no kanji, empty for null.
     */
    public static String kanjiCore(String word) {
        if (word == null) {
            return "";
        }
        int first = -1;
        int last = -1;
        for (int i = 0; i < word.length(); i++) {
            if (Character.UnicodeScript.of(word.charAt(i)) == Character.UnicodeScript.HAN) {
                first = first < 0 ? i : first;
                last = i;
            }
        }
        return first < 0 ? word : word.substring(first, last + 1);
    }
}
