package com.github.enerccio.lingollm.domain.service.impl.languages;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.collections.CEFR;
import com.github.enerccio.lingollm.domain.factories.TestQuestionFactory;
import com.github.enerccio.lingollm.domain.factories.impl.KanjiReadingFactory;
import com.github.enerccio.lingollm.domain.service.dto.TestQuestionRequest;
import com.github.enerccio.lingollm.domain.service.impl.BaseLanguageDefinitionService;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageStringBuilder;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageStringMetadata;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageStrings;
import com.github.enerccio.lingollm.ui.lang.languages.japanese.Furigana;
import com.github.enerccio.lingollm.ui.lang.languages.japanese.JlptLevel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Japanese: the application shows furigana over every kanji. The model writes the questions as plain text and a pass of
 * its own, after every batch, adds the furigana to the texts that have kanji. The
 * level test ends with {@value #KANJI_QUESTIONS} questions about the reading of a kanji in a sentence.
 */
public class JapaneseLanguageDefinitionServiceImpl extends BaseLanguageDefinitionService {

    /** The most missing furigana reported for one text, the rest would only repeat it. */
    private static final int MAX_REPORTED = 3;

    private static final String TARGET_KANJI = "targetKanji";
    private static final String READINGS = "readings";
    private static final String WORD = "word";
    private static final String READING = "reading";

    public static final String SECTION_KANJI = "kanji";
    public static final int KANJI_QUESTIONS = 10;

    public JapaneseLanguageDefinitionServiceImpl() {
        super(Languages.JAPANESE);
    }

    /**
     * The questions are written as plain text: the readings are a pass of their own after every batch, see
     * {@link #annotatedTextSchema()}.
     */
    @Override
    protected void customizeLanguageStringSchema(ObjectNode schema) {
        schema.removeAll();
        schema.put("type", "object");
        schema.put("description", "A text, written as a Japanese writes it, without furigana");
        schema.putObject("properties").putObject(LanguageStrings.RAW_STRING).put("type", "string").put("minLength", 1)
                .put("description", "The text itself, plain, without furigana, readings or any markup");
        schema.putArray("required").add(LanguageStrings.RAW_STRING);
        schema.put("additionalProperties", false);
    }

    @Override
    public String getLanguageStringInstructions() {
        return """
                Every text is an object {"rawString": "..."}: the text as a Japanese writes it (kanji and kana), never romaji. Do
                not write readings or furigana, they are added later.""";
    }

    // --- the furigana pass ---

    @Override
    protected boolean needsAnnotation(String text) {
        return hasKanji(text);
    }

    /**
     * The model does not write the text again and does not count characters: it lists the words with kanji in the order
     * of the text, each with its reading, and {@link #readAnnotatedText} finds them in the text.
     */
    @Override
    protected ObjectNode annotatedTextSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.put("description", "The readings of the words with kanji of a text, in the order of the text");

        ObjectNode entry = schema.putObject("properties").putObject(READINGS).put("type", "array")
                .put("description", "Every kanji of the text is in one of the words, a word of the text again has its entry again")
                .putObject("items");
        entry.put("type", "object");
        ObjectNode entryProperties = entry.putObject("properties");
        entryProperties.putObject(WORD).put("type", "string").put("minLength", 1)
                .put("description", "One kanji or a word of kanji exactly as written in the text (the kana right after it, okurigana, may be in it)");
        entryProperties.putObject(READING).put("type", "string").put("minLength", 1)
                .put("description", "The reading of the word in hiragana, as it is read in this text");
        entry.putArray("required").add(WORD).add(READING);
        entry.put("additionalProperties", false);

        schema.putArray("required").add(READINGS);
        schema.put("additionalProperties", false);
        return schema;
    }

    /** The words a question asks the reading of: their furigana must not be split, or the question would give it away. */
    @Override
    protected Set<String> wholeWords(JsonArray questions) {
        Set<String> words = new LinkedHashSet<>();
        for (JsonElement question : questions) {
            if (question.isJsonObject() && question.getAsJsonObject().has(TARGET_KANJI)
                    && question.getAsJsonObject().get(TARGET_KANJI).isJsonPrimitive()) {
                String word = question.getAsJsonObject().get(TARGET_KANJI).getAsString();
                if (hasKanji(word)) {
                    words.add(word);
                }
            }
        }
        return words;
    }

    /**
     * Makes the furigana of the text from the readings the model listed: every word is looked for in the text after the
     * word before it (anywhere when it is not there), kana the reading repeats at the start or end of the word (お金 /
     * おかね, 食べ / たべ) is taken out of the furigana. A word that is in the text again without an entry gets the reading
     * of the entry, a word that is not in the text is ignored (and named when a kanji has no reading). What the model wrote besides the words does not matter, so the text can't be wrong.
     */
    @Override
    protected LanguageString readAnnotatedText(JsonElement answer, String expected, Set<String> wholeWords, List<String> problems) {
        JsonArray entries = answer != null && answer.isJsonObject() && answer.getAsJsonObject().get(READINGS) != null
                && answer.getAsJsonObject().get(READINGS).isJsonArray() ? answer.getAsJsonObject().getAsJsonArray(READINGS) : null;
        if (entries == null) {
            problems.add("no readings");
            return null;
        }
        boolean[] taken = new boolean[expected.length()];
        List<int[]> placed = new ArrayList<>();
        List<String> values = new ArrayList<>();
        Map<String, String> known = new LinkedHashMap<>();
        List<String> unknown = new ArrayList<>();
        int cursor = 0;
        for (JsonElement element : entries) {
            JsonObject entry = element.isJsonObject() ? element.getAsJsonObject() : null;
            String word = text(entry, WORD);
            String reading = text(entry, READING);
            if (word.isEmpty() || reading.isEmpty()) {
                problems.add("an entry needs a word and its reading: " + element);
                continue;
            }
            if (!hasKanji(word)) {
                continue;
            }
            int at = find(expected, word, cursor, taken);
            if (at < 0) {
                at = find(expected, word, 0, taken);
            }
            if (at < 0) {
                // an extra word (a gloss, a typo) does not hurt: what counts is that every kanji of the text is read
                unknown.add(word);
                continue;
            }
            place(expected, at, word, reading, taken, placed, values);
            known.put(word, reading);
            cursor = at + word.length();
        }
        // the words that came again
        known.keySet().stream().sorted(Comparator.comparingInt(String::length).reversed()).forEach(word -> {
            for (int at = find(expected, word, 0, taken); at >= 0; at = find(expected, word, at + 1, taken)) {
                place(expected, at, word, known.get(word), taken, placed, values);
            }
        });
        if (!problems.isEmpty()) {
            return null;
        }

        List<String> missing = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        for (int i = 0; i <= expected.length(); i++) {
            if (i < expected.length() && isHan(expected.charAt(i)) && !taken[i]) {
                run.append(expected.charAt(i));
            } else if (!run.isEmpty()) {
                missing.add(run.toString());
                run.setLength(0);
            }
        }
        if (!missing.isEmpty()) {
            problems.add("no reading for " + String.join(", ", missing.subList(0, Math.min(MAX_REPORTED, missing.size())))
                    + (missing.size() > MAX_REPORTED ? " and " + (missing.size() - MAX_REPORTED) + " more" : "")
                    + (unknown.isEmpty() ? "" : " (the words " + String.join(", ", unknown) + " are not in the text, the entries must be written exactly as in the text)"));
            return null;
        }

        LanguageStringBuilder builder = LanguageString.builder().append(expected);
        List<Integer> order = new ArrayList<>(java.util.stream.IntStream.range(0, placed.size()).boxed().toList());
        order.sort(Comparator.comparingInt(i -> placed.get(i)[0]));
        for (int i : order) {
            Furigana.annotate(builder, placed.get(i)[0], placed.get(i)[1], values.get(i));
        }
        LanguageString string = builder.build();

        List<LanguageStringMetadata> furigana = Furigana.of(string);
        for (String word : wholeWords) {
            String core = Furigana.kanjiCore(word);
            int offset = word.indexOf(core);
            for (int at = expected.indexOf(word); at >= 0; at = expected.indexOf(word, at + 1)) {
                int start = at + offset;
                int end = start + core.length();
                if (furigana.stream().noneMatch(f -> f.getStart() <= start && end <= f.getEnd())) {
                    problems.add("\"" + word + "\" must be one word with one reading, it is split");
                    return null;
                }
            }
        }
        return string;
    }

    private static String text(JsonObject entry, String key) {
        return entry != null && entry.has(key) && entry.get(key).isJsonPrimitive() ? entry.get(key).getAsString().strip() : "";
    }

    /** Where the word is in the text from {@code from} on, and none of it is read already; -1 when nowhere. */
    private static int find(String text, String word, int from, boolean[] taken) {
        for (int at = text.indexOf(word, Math.max(from, 0)); at >= 0; at = text.indexOf(word, at + 1)) {
            boolean free = true;
            for (int i = at; i < at + word.length() && free; i++) {
                free = !taken[i];
            }
            if (free) {
                return at;
            }
        }
        return -1;
    }

    /** Furigana over the word at {@code at}, without the kana at its ends that the reading just repeats. */
    private static void place(String text, int at, String word, String reading, boolean[] taken, List<int[]> placed,
                              List<String> values) {
        int prefix = 0;
        while (prefix < word.length() - 1 && prefix < reading.length() - 1 && !isHan(word.charAt(prefix))
                && word.charAt(prefix) == reading.charAt(prefix)) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < word.length() - prefix - 1 && suffix < reading.length() - prefix - 1
                && !isHan(word.charAt(word.length() - 1 - suffix))
                && word.charAt(word.length() - 1 - suffix) == reading.charAt(reading.length() - 1 - suffix)) {
            suffix++;
        }
        java.util.Arrays.fill(taken, at, at + word.length(), true);
        placed.add(new int[]{at + prefix, at + word.length() - suffix});
        values.add(reading.substring(prefix, reading.length() - suffix));
    }

    private static boolean hasKanji(String text) {
        return text.codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private static boolean isHan(char c) {
        return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN;
    }


    @Override
    protected Map<String, TestQuestionFactory> questionKinds() {
        Map<String, TestQuestionFactory> kinds = super.questionKinds();
        KanjiReadingFactory factory = new KanjiReadingFactory();
        kinds.put(handlerName(factory), factory);
        return kinds;
    }

    /**
     * The kanji questions grow harder with their levels, spread over A1-C2: the target is a kanji of the JLPT level that
     * is about the CEFR level of the question.
     */
    @Override
    protected List<TestQuestionRequest> questionsAtEnd() {
        String type = handlerName(new KanjiReadingFactory());
        List<CEFR> levels = spreadLevels(KANJI_QUESTIONS);
        List<TestQuestionRequest> requests = new ArrayList<>();
        for (CEFR level : levels) {
            TestQuestionRequest request = new TestQuestionRequest(SECTION_KANJI, type,
                    "the target is a kanji (or a word of kanji) of the JLPT level " + jlptOf(level));
            request.setLevel(level);
            requests.add(request);
        }
        return requests;
    }

    /** The JLPT level that is about the CEFR one. */
    static JlptLevel jlptOf(CEFR level) {
        return switch (level) {
            case A1 -> JlptLevel.N5;
            case A2 -> JlptLevel.N4;
            case B1 -> JlptLevel.N3;
            case B2 -> JlptLevel.N2;
            case C1, C2 -> JlptLevel.N1;
        };
    }

}
