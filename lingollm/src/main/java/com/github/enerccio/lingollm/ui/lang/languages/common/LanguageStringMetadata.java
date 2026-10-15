package com.github.enerccio.lingollm.ui.lang.languages.common;

import com.github.enerccio.lingollm.domain.traits.LLMField;
import java.util.Objects;

/**
 * Something known about a part of a {@link LanguageString}: the {@link #getType() kind} of the metadata (what reads
 * it, like {@code furigana}), the part it covers and a {@link #getValue() value} whose meaning belongs to the kind.
 * The part is {@code [start, end)}, indexes into the raw string as for {@link String#substring(int, int)}.
 * <p>
 * Read only, create it with {@link LanguageStringBuilder}.
 */
public final class LanguageStringMetadata {

    @LLMField(description = "The kind of the annotation, e.g. furigana")
    private String type;
    @LLMField(minimum = 0, description = "Index of the first character covered, counted from 0 in the raw string")
    private int start;
    @LLMField(minimum = 1, description = "Index after the last character covered (exclusive), greater than start")
    private int end;
    @LLMField(description = "What the annotation says about the covered part, e.g. the reading of the kanji for furigana")
    private String value;

    // for deserialization
    private LanguageStringMetadata() {
    }

    LanguageStringMetadata(String type, int start, int end, String value) {
        this.type = type;
        this.start = start;
        this.end = end;
        this.value = value;
    }

    public String getType() {
        return type;
    }

    /**
     * @return index of the first character covered
     */
    public int getStart() {
        return start;
    }

    /**
     * @return index after the last character covered
     */
    public int getEnd() {
        return end;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LanguageStringMetadata that && start == that.start && end == that.end
                && Objects.equals(type, that.type) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, start, end, value);
    }

    @Override
    public String toString() {
        return type + "[" + start + "," + end + ")=" + value;
    }
}
