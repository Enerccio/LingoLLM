package com.github.enerccio.lingollm.ui.lang.languages.common.impl;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionHandler;
import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.ui.components.LanguageStringComponent;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import org.springframework.beans.factory.annotation.Configurable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Put the segments of a sentence in order. The question keeps them in the <b>correct</b> order, whoever shows the
 * question shuffles them (and compares the learner's order with the stored one).
 */
@Configurable
public class WordOrderQuestionHandler extends TestQuestionHandler<WordOrderQuestionData> {

    @Override
    public String getLlmDescription() {
        return "The learner puts the parts of a sentence in order. \"sentence\" is one correct sentence cut into 3 to 8 parts with a "
                + SEPARATOR + " between the parts, in their correct order (Ich|heisse|Anna.); the program shuffles the parts for the learner. "
                + "Cut at words or phrases, keep the punctuation with the part it belongs to.";
    }

    private static final String SEPARATOR = "|";
    private static final String SEGMENTS = "segments";
    private static final String SENTENCE = "sentence";

    private List<LanguageString> segments = List.of();
    // indexes of the segments in the shuffled order they are offered in, and in the order the learner put them
    private List<Integer> shuffled = new ArrayList<>();
    private List<Integer> placed = new ArrayList<>();
    private Div poolArea;
    private Div answerArea;

    public WordOrderQuestionHandler(Test test, TestQuestion testQuestion) {
        super(test, testQuestion);
    }

    @Override
    protected WordOrderQuestionData createEmpty() {
        return new WordOrderQuestionData();
    }

    @Override
    protected Class<WordOrderQuestionData> getType() {
        return WordOrderQuestionData.class;
    }

    /**
     * The model does not write a list of texts: it writes the sentence with the {@value #SEPARATOR} between its parts, which
     * is easier for it and shorter.
     */
    @Override
    public ObjectNode getLlmSchema() {
        ObjectNode schema = super.getLlmSchema().deepCopy();
        ObjectNode properties = (ObjectNode) schema.get("properties");
        properties.remove(SEGMENTS);
        properties.putObject(SENTENCE).put("type", "string").put("minLength", 3)
                .put("description", "One correct sentence cut into 2 to 8 parts, the parts in their correct order with a "
                        + SEPARATOR + " between them, e.g. Ich|heisse|Anna. The program shuffles the parts for the learner");
        ArrayNode required = schema.putArray("required");
        required.add("instructions").add(SENTENCE);
        return schema;
    }

    /** The sentence cut by the separator becomes the segments, the empty parts are left out. */
    @Override
    public void normalizeLlmData(JsonObject data) {
        if (!data.has(SENTENCE) || !data.get(SENTENCE).isJsonPrimitive()) {
            return;
        }
        JsonArray segments = new JsonArray();
        for (String part : data.remove(SENTENCE).getAsString().split(Pattern.quote(SEPARATOR))) {
            if (!part.isBlank()) {
                JsonObject text = new JsonObject();
                text.addProperty("rawString", part.strip());
                segments.add(text);
            }
        }
        data.add(SEGMENTS, segments);
    }

    @Override
    protected void check(WordOrderQuestionData data) {
        if (data.getSegments() == null || data.getSegments().size() < 2) {
            throw new IllegalArgumentException("A word order question needs a sentence of at least two parts, with a "
                    + SEPARATOR + " between the parts");
        }
        if (data.getSegments().stream().anyMatch(s -> s == null || s.getRawString().isBlank())) {
            throw new IllegalArgumentException("A segment can't be empty");
        }
    }

    /**
     * @param segments the segments of the sentence in the correct order
     */
    public void setSegments(List<LanguageString> segments) {
        WordOrderQuestionData data = getTestData(getType());
        data.setSegments(segments == null ? null : new ArrayList<>(segments));
        check(data);
        setTestData(data);
    }

    /**
     * The segments in a shuffled order in a pool, and below it the answer being built: a click takes a segment from the
     * pool to the end of the answer, a click on a segment of the answer puts it back. Buttons wrap, so it works on a
     * phone.
     */
    @Override
    public Component render() {
        WordOrderQuestionData data = getTestData(getType());
        segments = data.getSegments() == null ? List.of() : data.getSegments();
        shuffled = shuffle(segments.size());
        placed = new ArrayList<>();

        poolArea = new Div();
        poolArea.addClassName(CssStyles.WORD_ORDER_AREA);
        answerArea = new Div();
        answerArea.addClassNames(CssStyles.WORD_ORDER_AREA, CssStyles.WORD_ORDER_ANSWER);

        Span hint = new Span(loc.getValue(L.MSG_WORD_ORDER_HINT));
        hint.addClassNames(CssStyles.HINT_TEXT, CssStyles.WHITE_SPACE_NORMAL);

        redraw();
        return renderQuestion(hint, answerArea, poolArea);
    }

    private void redraw() {
        poolArea.removeAll();
        answerArea.removeAll();
        for (int index : shuffled) {
            if (!placed.contains(index)) {
                poolArea.add(segmentButton(index, () -> placed.add(index)));
            }
        }
        for (int index : placed) {
            answerArea.add(segmentButton(index, () -> placed.remove(Integer.valueOf(index))));
        }
    }

    private Button segmentButton(int index, Runnable move) {
        Button button = new Button();
        button.getElement().appendChild(new LanguageStringComponent(segments.get(index)).getElement());
        button.addClickListener(event -> {
            move.run();
            redraw();
        });
        return button;
    }

    /** The indexes in a random order, not the correct one unless the segments can't be told apart by position. */
    private static List<Integer> shuffle(int size) {
        List<Integer> order = new ArrayList<>(IntStream.range(0, size).boxed().toList());
        List<Integer> correct = List.copyOf(order);
        for (int attempt = 0; attempt < 10 && size > 1; attempt++) {
            Collections.shuffle(order);
            if (!order.equals(correct)) {
                break;
            }
        }
        return order;
    }

    @Override
    public void setAnswer(String answer) {
        if (poolArea == null || answer == null) {
            return;
        }
        try {
            List<Integer> restored = new ArrayList<>();
            for (JsonElement index : JsonParser.parseString(answer).getAsJsonArray()) {
                int value = index.getAsInt();
                if (value >= 0 && value < segments.size() && !restored.contains(value)) {
                    restored.add(value);
                }
            }
            placed = restored;
            redraw();
        } catch (RuntimeException e) {
            // not an answer of this question
        }
    }

    /**
     * @return JSON list of the indexes of the segments (in the order of the question data, which is the correct
     * order) in the order the learner put them in; null until all of them are placed
     */
    @Override
    public String getAnswer() {
        if (segments.isEmpty() || placed.size() != segments.size()) {
            return null;
        }
        return new Gson().toJson(placed);
    }
}
