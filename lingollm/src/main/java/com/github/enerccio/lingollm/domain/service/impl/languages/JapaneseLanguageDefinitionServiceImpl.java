package com.github.enerccio.lingollm.domain.service.impl.languages;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.service.impl.BaseLanguageDefinitionService;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageStringMetadata;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageStrings;
import com.github.enerccio.lingollm.ui.lang.languages.japanese.Furigana;

import java.util.ArrayList;
import java.util.List;

/**
 * Japanese: the application shows furigana over every kanji, so every text the model writes has to have them.
 */
public class JapaneseLanguageDefinitionServiceImpl extends BaseLanguageDefinitionService {

    /** The most missing furigana reported for one text, the rest would only repeat it. */
    private static final int MAX_REPORTED = 3;

    public JapaneseLanguageDefinitionServiceImpl() {
        super(Languages.JAPANESE);
    }

    @Override
    protected void customizeLanguageStringSchema(ObjectNode schema) {
        schema.put("description", "A text with furigana over every kanji in it");
        ArrayNode required = schema.putArray("required");
        required.add(LanguageStrings.RAW_STRING).add(LanguageStrings.METADATA);

        ObjectNode metadata = (ObjectNode) schema.get("properties").get(LanguageStrings.METADATA);
        metadata.put("description", "The furigana of the text: one entry for every kanji or run of kanji, empty when the text has no kanji");
        ObjectNode type = (ObjectNode) metadata.get("items").get("properties").get("type");
        type.put("const", Furigana.TYPE);
    }

    @Override
    public String getLanguageStringInstructions() {
        return """
                Every text is an object {"rawString": "...", "metadata": [...]}. "rawString" is the plain text, never romaji.
                Every kanji in a text (the English instructions have none) must be covered by a furigana entry in "metadata":
                {"type": "furigana", "start": 0, "end": 1, "value": "た"}. "start" is the index of the first character covered
                and "end" the index after the last one, counted in characters of "rawString" from 0. "value" is the reading in
                hiragana. Cover only the kanji, not the kana after them: in "食べる" the entry covers 食 (start 0, end 1, value
                "た"); a word of several kanji gets one entry, "日本語" (start 0, end 3, value "にほんご"). The entries do not
                overlap. A text without kanji has "metadata": [].""";
    }

    @Override
    public List<String> checkLanguageString(LanguageString string) {
        List<String> problems = new ArrayList<>(super.checkLanguageString(string));
        String text = string.getRawString();
        List<LanguageStringMetadata> furigana = Furigana.of(string);
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int index = i;
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN
                    && furigana.stream().noneMatch(f -> f.getStart() <= index && index < f.getEnd())) {
                missing.add(new String(Character.toChars(codePoint)) + " (" + index + ")");
            }
            i += Character.charCount(codePoint);
        }
        if (!missing.isEmpty()) {
            problems.add("\"" + text + "\": no furigana over " + String.join(", ",
                    missing.subList(0, Math.min(MAX_REPORTED, missing.size())))
                    + (missing.size() > MAX_REPORTED ? " and " + (missing.size() - MAX_REPORTED) + " more" : ""));
        }
        return problems;
    }

}
