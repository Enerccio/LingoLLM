package com.github.enerccio.lingollm.ui;

import com.github.enerccio.lingollm.ui.components.LanguageStringComponent;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.japanese.Furigana;
import com.vaadin.flow.dom.Element;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LanguageStringComponent}: draws the annotations of a text over the parts they cover.
 */
class LanguageStringComponentTest {

    /** 学校へ行く with the readings of both words. */
    private static LanguageString sentence() {
        var builder = LanguageString.builder();
        Furigana.append(builder, "学校", "がっこう");
        builder.append("へ");
        Furigana.append(builder, "行", "い");
        return builder.append("く").build();
    }

    static List<Element> elements(Element root, String tag) {
        List<Element> found = new ArrayList<>();
        root.getChildren().filter(child -> !child.isTextNode()).forEach(child -> {
            if (child.getTag().equals(tag)) {
                found.add(child);
            }
            found.addAll(elements(child, tag));
        });
        return found;
    }

    @Test
    void furiganaIsRubyOverTheCoveredText() {
        LanguageStringComponent component = new LanguageStringComponent(sentence());

        List<Element> ruby = elements(component.getElement(), "ruby");
        assertThat(ruby).hasSize(2);
        assertThat(ruby.get(0).getTextRecursively()).isEqualTo("学校がっこう");
        assertThat(elements(ruby.get(0), "rt").getFirst().getText()).isEqualTo("がっこう");
        assertThat(component.getElement().getTextRecursively()).isEqualTo("学校がっこうへ行いく");
    }

    @Test
    void aFilterHidesTheFuriganaOverTheTarget() {
        LanguageStringComponent component = new LanguageStringComponent(sentence());

        component.setAnnotationFilter(Furigana.hideOver("学校"));

        List<Element> ruby = elements(component.getElement(), "ruby");
        assertThat(ruby).hasSize(1);
        assertThat(ruby.getFirst().getTextRecursively()).isEqualTo("行い");
        // the text itself is still all there
        assertThat(component.getElement().getTextRecursively()).isEqualTo("学校へ行いく");

        component.setAnnotationFilter(null);
        assertThat(elements(component.getElement(), "ruby")).hasSize(2);
    }

    @Test
    void aTargetInsideTheCoveredWordHidesTheWholeReading() {
        LanguageStringComponent component = new LanguageStringComponent(sentence());

        component.setAnnotationFilter(Furigana.hideOver("校"));

        assertThat(elements(component.getElement(), "ruby")).hasSize(1);
    }

    @Test
    void metadataOfAnUnknownKindIsIgnoredAndTheTextKept() {
        LanguageString string = LanguageString.builder().append("abc", "pitch", "high").append("def").build();

        LanguageStringComponent component = new LanguageStringComponent(string);

        assertThat(component.getElement().getTextRecursively()).isEqualTo("abcdef");
        assertThat(elements(component.getElement(), "ruby")).isEmpty();

        component.registerAnnotator("pitch", (text, metadata) -> new Element("em").setText(text + metadata.getValue()));
        assertThat(elements(component.getElement(), "em")).hasSize(1);
        assertThat(component.getElement().getTextRecursively()).isEqualTo("abchighdef");
    }

    @Test
    void overlappingAnnotationsDrawTheFirstOnly() {
        LanguageString string = LanguageString.builder().append("日本語")
                .annotate(Furigana.TYPE, 0, 2, "にほん").annotate(Furigana.TYPE, 1, 3, "ほんご").build();

        LanguageStringComponent component = new LanguageStringComponent(string);

        assertThat(elements(component.getElement(), "ruby")).hasSize(1);
        assertThat(component.getElement().getTextRecursively()).isEqualTo("日本にほん語");
    }

    @Test
    void textIsEscapedNotInterpreted() {
        LanguageStringComponent component = new LanguageStringComponent(LanguageString.of("<b>x</b>"));

        assertThat(elements(component.getElement(), "b")).isEmpty();
        assertThat(component.getElement().getTextRecursively()).isEqualTo("<b>x</b>");
    }

    @Test
    void nothingToShowForNoString() {
        LanguageStringComponent component = new LanguageStringComponent();

        assertThat(component.getElement().getChildCount()).isZero();
    }
}
