package com.github.enerccio.lingollm.ui.lang.languages.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Finding {@link LanguageString}s in data the model wrote, and the checks that hold for all of them.
 */
public final class LanguageStrings {

    public static final String RAW_STRING = "rawString";
    public static final String METADATA = "metadata";

    private static final Gson GSON = new Gson();

    private LanguageStrings() {
    }

    /**
     * Every language string in the JSON, wherever it is: the objects that have a {@code rawString} text. The nested
     * ones of a language string (its metadata) are not looked at.
     */
    public static List<LanguageString> findIn(JsonElement json) {
        List<LanguageString> found = new ArrayList<>();
        collect(json, found);
        return found;
    }

    private static void collect(JsonElement json, List<LanguageString> found) {
        if (json == null) {
            return;
        }
        if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(element -> collect(element, found));
        } else if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            if (object.has(RAW_STRING) && object.get(RAW_STRING).isJsonPrimitive()) {
                found.add(GSON.fromJson(object, LanguageString.class));
            } else {
                object.entrySet().forEach(entry -> collect(entry.getValue(), found));
            }
        }
    }

    /**
     * Whether the JSON schema describes a language string: an object with the properties {@code rawString} and
     * {@code metadata} and nothing else.
     */
    public static boolean isSchemaOf(JsonNode schema) {
        if (schema == null || !schema.has("properties")) {
            return false;
        }
        JsonNode properties = schema.get("properties");
        return properties.size() == 2 && properties.has(RAW_STRING) && properties.has(METADATA);
    }

    /**
     * What is wrong with the metadata of any language: a part that is not inside the text, or has no kind or value.
     *
     * @return the problems, empty when there are none
     */
    public static List<String> checkMetadata(LanguageString string) {
        List<String> problems = new ArrayList<>();
        int length = string.getRawString().length();
        for (LanguageStringMetadata metadata : string.getMetadata()) {
            if (metadata.getType() == null || metadata.getType().isBlank() || metadata.getValue() == null) {
                problems.add("\"" + string.getRawString() + "\": metadata without a type or a value");
            } else if (metadata.getStart() < 0 || metadata.getEnd() > length || metadata.getStart() >= metadata.getEnd()) {
                problems.add("\"" + string.getRawString() + "\": " + metadata.getType() + " covers [" + metadata.getStart()
                        + ", " + metadata.getEnd() + ") which is not a part of the text (" + length + " characters)");
            }
        }
        return problems;
    }
}
