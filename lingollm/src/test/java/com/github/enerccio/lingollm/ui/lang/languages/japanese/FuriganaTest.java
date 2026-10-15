package com.github.enerccio.lingollm.ui.lang.languages.japanese;

import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageStringBuilder;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageStringMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FuriganaTest {

    @Test
    void textIsAppendedWithItsReading() {
        LanguageStringBuilder builder = LanguageString.builder();
        Furigana.append(builder, "日本語", "にほんご");
        LanguageString string = builder.append("を").build();

        List<LanguageStringMetadata> furigana = Furigana.of(string);
        assertThat(string.getRawString()).isEqualTo("日本語を");
        assertThat(furigana).hasSize(1);
        assertThat(furigana.get(0).getType()).isEqualTo(Furigana.TYPE);
        assertThat(string.textOf(furigana.get(0))).isEqualTo("日本語");
        assertThat(furigana.get(0).getValue()).isEqualTo("にほんご");
    }

    @Test
    void severalReadingsAreOrderedByPosition() {
        LanguageStringBuilder builder = LanguageString.builder().append("私は").append("学生", "x", "y");
        // added out of order, and next to metadata that is not furigana
        Furigana.annotate(builder, 2, 4, "がくせい");
        Furigana.annotate(builder, 0, 1, "わたし");
        LanguageString string = builder.build();

        List<LanguageStringMetadata> furigana = Furigana.of(string);
        assertThat(furigana).extracting(string::textOf).containsExactly("私", "学生");
        assertThat(furigana).extracting(LanguageStringMetadata::getValue).containsExactly("わたし", "がくせい");
        assertThat(string.getMetadata()).hasSize(3);
    }

    @Test
    void anyTextIsAcceptedAsTheReading() {
        LanguageStringBuilder builder = LanguageString.builder().append("日本");

        Furigana.annotate(builder, 0, 2, "nihon");
        Furigana.annotate(builder, 0, 1, "ni ほん?");
        LanguageString string = builder.build();

        assertThat(Furigana.of(string)).extracting(LanguageStringMetadata::getValue).containsExactly("ni ほん?", "nihon");
    }

    @Test
    void aReadingNeedsAPartOfTheTextAndAValue() {
        LanguageStringBuilder builder = LanguageString.builder().append("日本");

        assertThatThrownBy(() -> Furigana.annotate(builder, 0, 3, "にほん")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Furigana.annotate(builder, 0, 2, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
