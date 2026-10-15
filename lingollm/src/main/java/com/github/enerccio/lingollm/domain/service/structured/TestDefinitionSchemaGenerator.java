package com.github.enerccio.lingollm.domain.service.structured;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.enerccio.lingollm.domain.service.LanguageDefinitionService;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageStrings;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionHandler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the JSON schema of a test the model writes: an object with the list of {@code questions}, in the order they
 * are asked. Every question is one of the kinds the handlers stand for; it says which in {@code type}
 * ({@link TestQuestionHandler#getLlmTypeName()}) and has the properties of the schema of that handler
 * ({@link TestQuestionHandler#getLlmSchema()}).
 */
public final class TestDefinitionSchemaGenerator {

    public static final String QUESTIONS = "questions";
    public static final String TYPE = "type";

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private TestDefinitionSchemaGenerator() {
    }

    /**
     * @param handlers the kinds of questions the model may use, at least one
     * @return the complete schema (no references)
     * @throws IllegalArgumentException when there are no handlers or two have the same type name
     */
    public static ObjectNode generate(List<? extends TestQuestionHandler<?>> handlers) {
        return generate(handlers, null);
    }

    /**
     * Like {@link #generate(List)}, and every text (a {@link com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString})
     * in the schema is described by the language: where the handlers have the generic schema of a text, the schema of
     * {@link LanguageDefinitionService#getLanguageStringSchema()} is put instead.
     *
     * @param language null to keep the generic text schema
     */
    public static ObjectNode generate(List<? extends TestQuestionHandler<?>> handlers, LanguageDefinitionService language) {
        if (handlers == null || handlers.isEmpty()) {
            throw new IllegalArgumentException("At least one kind of question is needed");
        }
        ArrayNode kinds = NODES.arrayNode();
        Set<String> names = new HashSet<>();
        for (TestQuestionHandler<?> handler : handlers) {
            if (!names.add(handler.getLlmTypeName())) {
                throw new IllegalArgumentException("Two kinds of questions are named " + handler.getLlmTypeName());
            }
            kinds.add(questionSchema(handler));
        }

        ObjectNode items = NODES.objectNode();
        if (kinds.size() == 1) {
            items.setAll((ObjectNode) kinds.get(0));
        } else {
            items.set("oneOf", kinds);
        }

        ObjectNode questions = NODES.objectNode();
        questions.put("type", "array");
        questions.put("minItems", 1);
        questions.put("description", "The questions of the test in the order they are asked");
        questions.set("items", items);

        ObjectNode schema = NODES.objectNode();
        schema.put("type", "object");
        schema.set("properties", NODES.objectNode().set(QUESTIONS, questions));
        schema.set("required", NODES.arrayNode().add(QUESTIONS));
        schema.put("additionalProperties", false);
        if (language != null) {
            replaceLanguageStrings(schema, language.getLanguageStringSchema());
        }
        return schema;
    }

    /**
     * Puts the schema of the language where the schema describes a text. What the place says about the text (its
     * description, "what the learner is asked to do") stays in front of what the language says.
     */
    private static void replaceLanguageStrings(JsonNode node, ObjectNode replacement) {
        if (node.isArray()) {
            node.forEach(element -> replaceLanguageStrings(element, replacement));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        ObjectNode object = (ObjectNode) node;
        if (LanguageStrings.isSchemaOf(object)) {
            JsonNode placeDescription = object.get("description");
            JsonNode languageDescription = replacement.get("description");
            object.removeAll();
            object.setAll(replacement.deepCopy());
            if (placeDescription != null) {
                object.put("description", languageDescription == null ? placeDescription.asText()
                        : placeDescription.asText() + " " + languageDescription.asText());
            }
            return;
        }
        object.forEach(child -> replaceLanguageStrings(child, replacement));
    }

    /** The schema of the handler with the {@code type} that tells the kinds apart added to it. */
    private static ObjectNode questionSchema(TestQuestionHandler<?> handler) {
        ObjectNode schema = handler.getLlmSchema().deepCopy();

        ObjectNode type = NODES.objectNode();
        type.put("type", "string");
        type.put("const", handler.getLlmTypeName());

        ObjectNode properties = schema.has("properties") ? (ObjectNode) schema.get("properties") : schema.putObject("properties");
        // the type goes first in the object, the models fill it in in the order of the schema
        ObjectNode withType = NODES.objectNode();
        withType.set(TYPE, type);
        withType.setAll(properties);
        schema.set("properties", withType);

        ArrayNode required = NODES.arrayNode().add(TYPE);
        JsonNode existing = schema.get("required");
        if (existing != null) {
            required.addAll((ArrayNode) existing);
        }
        schema.set("required", required);
        return schema;
    }

}
