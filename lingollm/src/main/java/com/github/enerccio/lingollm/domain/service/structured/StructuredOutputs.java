package com.github.enerccio.lingollm.domain.service.structured;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.enerccio.lingollm.domain.traits.LLMField;
import com.github.enerccio.lingollm.domain.traits.LLMSchema;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Structured answers of the model: the JSON schema of a Java class ({@link LLMSchema}, {@link LLMField}), the check of
 * an answer against a schema and its reading into the class.
 */
public final class StructuredOutputs {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

    private static final JsonSchemaFactory VALIDATORS = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    /** The model wraps the JSON in a markdown block, often even when told not to. */
    private static final Pattern FENCE = Pattern.compile("^\\s*```[a-zA-Z]*\\s*\\n(.*?)\\n?\\s*```\\s*$", Pattern.DOTALL);

    private static final SchemaGenerator SCHEMAS = new SchemaGenerator(configure());

    private StructuredOutputs() {
    }

    private static SchemaGeneratorConfig configure() {
        SchemaGeneratorConfigBuilder builder = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                .with(new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED, JacksonOption.RESPECT_JSONPROPERTY_ORDER))
                .with(Option.INLINE_ALL_SCHEMAS)
                .with(Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT)
                .without(Option.SCHEMA_VERSION_INDICATOR);

        builder.forTypesInGeneral().withDescriptionResolver(scope -> {
            LLMSchema schema = scope.getType().getErasedType().getAnnotation(LLMSchema.class);
            return schema != null && !schema.description().isEmpty() ? schema.description() : null;
        });

        SchemaGeneratorConfigPart<FieldScope> fields = builder.forFields();
        // an unannotated field is required, as is a parameter of a function
        fields.withRequiredCheck(field -> {
            LLMField annotation = annotation(field);
            return annotation == null || annotation.required();
        });
        fields.withDescriptionResolver(field -> {
            LLMField annotation = annotation(field);
            return annotation != null && !annotation.description().isEmpty() ? annotation.description() : null;
        });
        fields.withNumberInclusiveMinimumResolver(field -> number(field, LLMField::minimum, Double.NEGATIVE_INFINITY));
        fields.withNumberInclusiveMaximumResolver(field -> number(field, LLMField::maximum, Double.POSITIVE_INFINITY));
        fields.withStringMinLengthResolver(field -> limit(field, LLMField::minLength));
        fields.withStringMaxLengthResolver(field -> limit(field, LLMField::maxLength));
        fields.withArrayMinItemsResolver(field -> limit(field, LLMField::minItems));
        fields.withArrayMaxItemsResolver(field -> limit(field, LLMField::maxItems));
        return builder.build();
    }

    private static LLMField annotation(MemberScope<?, ?> member) {
        return member.getAnnotationConsideringFieldAndGetter(LLMField.class);
    }

    private static BigDecimal number(FieldScope field, Function<LLMField, Double> limit, double none) {
        LLMField annotation = annotation(field);
        return annotation == null || limit.apply(annotation) == none ? null : BigDecimal.valueOf(limit.apply(annotation));
    }

    private static Integer limit(FieldScope field, Function<LLMField, Integer> limit) {
        LLMField annotation = annotation(field);
        return annotation == null || limit.apply(annotation) < 0 ? null : limit.apply(annotation);
    }

    /**
     * The JSON schema of the class, complete (no references to other schemas), from its fields; what the Jackson
     * annotations say about them (names, order, descriptions) counts too.
     */
    public static ObjectNode schemaOf(Class<?> type) {
        return SCHEMAS.generateSchema(type);
    }

    /**
     * Checks what the model wrote against the schema. Never throws: text that is not JSON is an invalid answer.
     */
    public static SchemaCheck validate(JsonNode schema, String text) {
        JsonNode answer;
        try {
            answer = MAPPER.readTree(cleanJson(text));
        } catch (Exception e) {
            return SchemaCheck.invalid(List.of("Not valid JSON: " + e.getMessage()));
        }
        if (answer == null || answer.isMissingNode()) {
            return SchemaCheck.invalid(List.of("Not valid JSON: the answer is empty"));
        }
        JsonSchema compiled = VALIDATORS.getSchema(schema);
        List<String> errors = compiled.validate(answer).stream().map(ValidationMessage::getMessage).sorted().toList();
        return errors.isEmpty() ? SchemaCheck.ok() : SchemaCheck.invalid(errors);
    }

    /**
     * Reads the answer into the class.
     *
     * @throws StructuredOutputException when the answer is not JSON of that class
     */
    public static <T> T parse(String text, Class<T> type) throws StructuredOutputException {
        try {
            return MAPPER.readValue(cleanJson(text), type);
        } catch (Exception e) {
            throw new StructuredOutputException(text, List.of(e.getMessage()), e);
        }
    }

    /** The text without the markdown block around it. */
    public static String cleanJson(String text) {
        if (text == null) {
            return "";
        }
        Matcher fence = FENCE.matcher(text);
        return (fence.matches() ? fence.group(1) : text).trim();
    }

}
