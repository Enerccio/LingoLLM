package com.github.enerccio.lingollm.ui.lang.languages.common;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LanguageStringTest {

    private static final Gson GSON = new Gson();

    @Test
    void aStringWithoutMetadata() {
        LanguageString string = LanguageString.of("hello");

        assertThat(string.getRawString()).isEqualTo("hello");
        assertThat(string.getMetadata()).isEmpty();
        assertThat(LanguageString.builder().build().getRawString()).isEmpty();
    }

    @Test
    void metadataIsSeparateFromTheText() {
        LanguageString string = LanguageString.builder()
                .append("abc")
                .append("def", "note", "first")
                .append("gh")
                .annotate("other", 1, 4, "second")
                .build();

        assertThat(string.getRawString()).isEqualTo("abcdefgh");
        assertThat(string.getMetadata()).extracting(LanguageStringMetadata::getType).containsExactly("note", "other");
        LanguageStringMetadata note = string.getMetadata("note").get(0);
        assertThat(note.getStart()).isEqualTo(3);
        assertThat(note.getEnd()).isEqualTo(6);
        assertThat(note.getValue()).isEqualTo("first");
        assertThat(string.textOf(note)).isEqualTo("def");
        assertThat(string.textOf(string.getMetadata("other").get(0))).isEqualTo("bcd");
    }

    @Test
    void overlappingAndRepeatedMetadataIsAllowed() {
        LanguageString string = LanguageString.builder().append("abcd")
                .annotate("t", 0, 3, "x").annotate("t", 1, 4, "y").annotate("t", 0, 3, "x").build();

        assertThat(string.getMetadata("t")).hasSize(3);
        assertThat(string.getMetadata("missing")).isEmpty();
    }

    @Test
    void theStringCannotBeChanged() {
        LanguageString string = LanguageString.builder().append("abc").annotate("t", 0, 1, "x").build();

        assertThatThrownBy(() -> string.getMetadata().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> string.getMetadata("t").clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void theBuilderCanGoOnAfterBuildingWithoutChangingWhatWasBuilt() {
        LanguageStringBuilder builder = LanguageString.builder().append("abc");
        LanguageString first = builder.build();

        builder.append("def").annotate("t", 0, 6, "x");

        assertThat(first.getRawString()).isEqualTo("abc");
        assertThat(first.getMetadata()).isEmpty();
        assertThat(builder.build().getRawString()).isEqualTo("abcdef");
    }

    @Test
    void invalidMetadataIsRefused() {
        LanguageStringBuilder builder = LanguageString.builder().append("abc");

        assertThatThrownBy(() -> builder.annotate("t", -1, 2, "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.annotate("t", 2, 2, "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.annotate("t", 1, 4, "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.annotate(" ", 0, 1, "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.annotate(null, 0, 1, "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.annotate("t", 0, 1, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.append("", "t", "x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toBuilderMakesAChangedCopy() {
        LanguageString original = LanguageString.builder().append("abc").annotate("t", 0, 1, "x").build();

        LanguageString changed = original.toBuilder().append("d").annotate("t", 3, 4, "y").build();

        assertThat(original.getRawString()).isEqualTo("abc");
        assertThat(original.getMetadata()).hasSize(1);
        assertThat(changed.getRawString()).isEqualTo("abcd");
        assertThat(changed.getMetadata()).hasSize(2);
    }

    @Test
    void survivesSerialization() {
        LanguageString string = LanguageString.builder().append("abc").append("de", "t", "x").annotate("u", 0, 2, "y").build();

        LanguageString read = GSON.fromJson(GSON.toJson(string), LanguageString.class);

        assertThat(read).isEqualTo(string);
        assertThat(read.hashCode()).isEqualTo(string.hashCode());
        assertThat(read.getMetadata()).containsExactlyElementsOf(string.getMetadata());
    }

    @Test
    void dataWithoutMetadataStillReads() {
        LanguageString read = GSON.fromJson("{\"rawString\":\"old\"}", LanguageString.class);

        assertThat(read).isEqualTo(LanguageString.of("old"));
        assertThat(read.getMetadata()).isEmpty();
        assertThat(GSON.fromJson("{}", LanguageString.class).getRawString()).isEmpty();
    }
}
