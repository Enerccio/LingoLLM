package com.github.enerccio.lingollm.ui.lang.languages.common;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Wrapper class around common strings - supports handling metadata (like furigana), which is kept apart from the
 * text as a list of {@link LanguageStringMetadata} over parts of it.
 * <p>
 * The class is read only, whoever gets one must not expect it to change and must not try to: build a new one with
 * {@link #builder()} (or {@link #of(String)} for a string without metadata). The fields are filled in only by the
 * deserialization of the question data.
 */
public final class LanguageString {

    private String rawString;
    private List<LanguageStringMetadata> metadata;

    // for deserialization
    private LanguageString() {
    }

    LanguageString(String rawString, List<LanguageStringMetadata> metadata) {
        this.rawString = rawString;
        this.metadata = List.copyOf(metadata);
    }

    /**
     * @return a string without metadata
     */
    public static LanguageString of(String rawString) {
        return builder().append(rawString).build();
    }

    public static LanguageStringBuilder builder() {
        return new LanguageStringBuilder();
    }

    /**
     * @return the text without any metadata, never null
     */
    public String getRawString() {
        return rawString == null ? "" : rawString;
    }

    /**
     * @return the metadata in the order it was added, never null, can't be changed
     */
    public List<LanguageStringMetadata> getMetadata() {
        return metadata == null ? List.of() : Collections.unmodifiableList(metadata);
    }

    /**
     * @return the metadata of the kind in the order it was added
     */
    public List<LanguageStringMetadata> getMetadata(String type) {
        return getMetadata().stream().filter(m -> Objects.equals(m.getType(), type)).toList();
    }

    /**
     * @return the part of the text the metadata covers
     */
    public String textOf(LanguageStringMetadata metadata) {
        return getRawString().substring(metadata.getStart(), metadata.getEnd());
    }

    /**
     * @return a builder that starts from this string, to make a changed copy
     */
    public LanguageStringBuilder toBuilder() {
        LanguageStringBuilder builder = new LanguageStringBuilder().append(getRawString());
        for (LanguageStringMetadata m : getMetadata()) {
            builder.annotate(m.getType(), m.getStart(), m.getEnd(), m.getValue());
        }
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LanguageString that && getRawString().equals(that.getRawString())
                && getMetadata().equals(that.getMetadata());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getRawString(), getMetadata());
    }

    @Override
    public String toString() {
        return getRawString() + (getMetadata().isEmpty() ? "" : " " + getMetadata());
    }
}
