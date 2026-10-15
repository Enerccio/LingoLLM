package com.github.enerccio.lingollm.ui.components;

import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageStringMetadata;
import com.github.enerccio.lingollm.ui.lang.languages.japanese.Furigana;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.dom.Element;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shows a {@link LanguageString} with its annotations: every {@link LanguageStringMetadata} of a kind that has an
 * {@link Annotator} is drawn over the part of the text it covers (furigana as {@code <ruby>}), the text between is
 * plain. Metadata of a kind nobody draws is ignored. When metadata overlaps, the one that starts first wins and the
 * ones that start inside it are not drawn.
 * <p>
 * An {@link AnnotationFilter} can hide annotations the learner is not supposed to see, e.g. the furigana over the kanji
 * a question asks the reading of, see {@link Furigana#hideOver(String)}. It is an inline component, so it can be used
 * as a label of a choice or a button.
 */
public class LanguageStringComponent extends Span {

    /**
     * Draws the annotation of one kind: wraps the text it covers into an element that also shows the metadata.
     */
    @FunctionalInterface
    public interface Annotator {

        /**
         * @param coveredText the part of the text the metadata covers
         * @param metadata    the annotation
         * @return the element to put in the place of the covered text, it has to contain the text
         */
        Element annotate(String coveredText, LanguageStringMetadata metadata);
    }

    /**
     * Decides which annotations are shown.
     */
    @FunctionalInterface
    public interface AnnotationFilter {

        /**
         * @param coveredText the part of the text the metadata covers
         * @return whether the annotation is drawn
         */
        boolean show(String coveredText, LanguageStringMetadata metadata);
    }

    private final Map<String, Annotator> annotators = new HashMap<>();

    private LanguageString value;
    private AnnotationFilter filter;

    public LanguageStringComponent() {
        this(null);
    }

    public LanguageStringComponent(LanguageString value) {
        addClassName(CssStyles.LANGUAGE_STRING);
        annotators.put(Furigana.TYPE, LanguageStringComponent::ruby);
        setValue(value);
    }

    public LanguageString getValue() {
        return value;
    }

    /**
     * Shows the string, null for nothing.
     */
    public void setValue(LanguageString value) {
        this.value = value;
        render();
    }

    /**
     * Hides the annotations the filter does not show, null shows all of them.
     */
    public void setAnnotationFilter(AnnotationFilter filter) {
        this.filter = filter;
        render();
    }

    /**
     * Makes the annotations of the kind drawn, or drawn differently from now on.
     */
    public void registerAnnotator(String type, Annotator annotator) {
        annotators.put(type, annotator);
        render();
    }

    private static Element ruby(String coveredText, LanguageStringMetadata metadata) {
        Element ruby = new Element("ruby");
        ruby.appendChild(Element.createText(coveredText));
        ruby.appendChild(new Element("rt").setText(metadata.getValue()));
        return ruby;
    }

    private void render() {
        getElement().removeAllChildren();
        if (value == null) {
            return;
        }
        String text = value.getRawString();
        int position = 0;
        for (LanguageStringMetadata metadata : drawn(text)) {
            if (metadata.getStart() < position) {
                continue;
            }
            if (metadata.getStart() > position) {
                getElement().appendChild(Element.createText(text.substring(position, metadata.getStart())));
            }
            getElement().appendChild(annotators.get(metadata.getType())
                    .annotate(text.substring(metadata.getStart(), metadata.getEnd()), metadata));
            position = metadata.getEnd();
        }
        if (position < text.length()) {
            getElement().appendChild(Element.createText(text.substring(position)));
        }
    }

    /**
     * The annotations to draw by their position, the ones outside of the text (the model may write anything) skipped.
     */
    private List<LanguageStringMetadata> drawn(String text) {
        List<LanguageStringMetadata> drawn = new ArrayList<>();
        for (LanguageStringMetadata metadata : value.getMetadata()) {
            if (!annotators.containsKey(metadata.getType()) || metadata.getStart() < 0
                    || metadata.getEnd() > text.length() || metadata.getStart() >= metadata.getEnd()) {
                continue;
            }
            if (filter == null || filter.show(text.substring(metadata.getStart(), metadata.getEnd()), metadata)) {
                drawn.add(metadata);
            }
        }
        drawn.sort(Comparator.comparingInt(LanguageStringMetadata::getStart).thenComparingInt(m -> -m.getEnd()));
        return drawn;
    }
}
