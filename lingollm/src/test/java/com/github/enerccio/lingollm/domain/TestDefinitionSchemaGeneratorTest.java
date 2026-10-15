package com.github.enerccio.lingollm.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.factories.impl.MultipleChoiceFactory;
import com.github.enerccio.lingollm.domain.factories.impl.ReadingComprehensionWithSingleChoiceFactory;
import com.github.enerccio.lingollm.domain.factories.impl.SingleChoiceFactory;
import com.github.enerccio.lingollm.domain.factories.impl.WordOrderQuestionFactory;
import com.github.enerccio.lingollm.domain.factories.impl.WritingQuestionFactory;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.domain.service.LanguageDefinitionService;
import com.github.enerccio.lingollm.domain.service.structured.SchemaCheck;
import com.github.enerccio.lingollm.domain.service.structured.StructuredOutputs;
import com.github.enerccio.lingollm.domain.service.structured.TestDefinitionSchemaGenerator;
import com.github.enerccio.lingollm.test.LingoLLMTestBase;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDefinitionSchemaGeneratorTest extends LingoLLMTestBase {

    @Autowired
    @Qualifier("japaneseLanguageDefinitionService")
    private LanguageDefinitionService japanese;

    @Autowired
    @Qualifier("germanLanguageDefinitionService")
    private LanguageDefinitionService german;

    private final Test test = new Test();
    private final TestQuestion question = new TestQuestion();

    private TestQuestionHandler<?> single() {
        return new SingleChoiceFactory().createHandler(test, question);
    }

    private TestQuestionHandler<?> multiple() {
        return new MultipleChoiceFactory().createHandler(test, question);
    }

    private TestQuestionHandler<?> reading() {
        return new ReadingComprehensionWithSingleChoiceFactory().createHandler(test, question);
    }

    @org.junit.jupiter.api.Test
    void handlerSchemaDescribesWhatTheModelWrites() {
        ObjectNode schema = single().getLlmSchema();

        assertThat(schema.get("properties").fieldNames()).toIterable()
                .containsExactlyInAnyOrder("instructions", "choices", "correctAnswer");
        assertThat(schema.get("required")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("instructions", "choices", "correctAnswer");
        assertThat(schema.get("properties").get("correctAnswer").get("description").asText()).contains("correct choice");
        // text with its annotations is spelled out, no references
        JsonNode choice = schema.get("properties").get("choices").get("items");
        assertThat(choice.get("properties").fieldNames()).toIterable().containsExactlyInAnyOrder("rawString", "metadata");
        assertThat(choice.get("required")).extracting(JsonNode::asText).containsExactly("rawString");
        assertThat(schema.toString()).doesNotContain("$ref");
    }

    @org.junit.jupiter.api.Test
    void readingHandlerAlsoAsksForTheText() {
        assertThat(reading().getLlmSchema().get("properties").has("text")).isTrue();
        assertThat(single().getLlmSchema().get("properties").has("text")).isFalse();
    }

    @org.junit.jupiter.api.Test
    void typeNamesOfTheHandlersDiffer() {
        assertThat(List.of(single(), multiple(), reading())).extracting(TestQuestionHandler::getLlmTypeName)
                .containsExactly("SingleChoiceHandler", "MultipleChoiceHandler", "ReadingComprehensionWithSingleChoiceHandler");
    }

    @org.junit.jupiter.api.Test
    void testSchemaAcceptsQuestionsOfAnyOfTheKinds() {
        ObjectNode schema = TestDefinitionSchemaGenerator.generate(List.of(single(), multiple()));

        String answer = """
                {"questions": [
                  {"type": "SingleChoiceHandler",
                   "instructions": {"rawString": "Pick the verb"},
                   "choices": [{"rawString": "hon"}, {"rawString": "食べる", "metadata": [
                       {"type": "furigana", "start": 0, "end": 2, "value": "たべる"}]}],
                   "correctAnswer": 1},
                  {"type": "MultipleChoiceHandler",
                   "instructions": {"rawString": "Pick the nouns"},
                   "choices": [{"rawString": "hon"}, {"rawString": "neko"}],
                   "correctAnswers": [0, 1]}
                ]}""";
        SchemaCheck check = StructuredOutputs.validate(schema, answer);
        assertThat(check.valid()).as(check.toString()).isTrue();
    }

    @org.junit.jupiter.api.Test
    void testSchemaRejectsWrongAnswers() {
        ObjectNode schema = TestDefinitionSchemaGenerator.generate(List.of(single(), multiple()));

        // unknown kind
        assertThat(StructuredOutputs.validate(schema, """
                {"questions": [{"type": "Essay", "instructions": {"rawString": "x"}}]}""").valid()).isFalse();
        // fields of another kind
        assertThat(StructuredOutputs.validate(schema, """
                {"questions": [{"type": "SingleChoiceHandler", "instructions": {"rawString": "x"},
                  "choices": [{"rawString": "a"}, {"rawString": "b"}], "correctAnswers": [0]}]}""").valid()).isFalse();
        // no questions
        assertThat(StructuredOutputs.validate(schema, "{\"questions\": []}").valid()).isFalse();
        // a single choice with one choice only
        assertThat(StructuredOutputs.validate(schema, """
                {"questions": [{"type": "SingleChoiceHandler", "instructions": {"rawString": "x"},
                  "choices": [{"rawString": "a"}], "correctAnswer": 0}]}""").valid()).isFalse();
    }

    @org.junit.jupiter.api.Test
    void oneKindNeedsNoChoice() {
        ObjectNode schema = TestDefinitionSchemaGenerator.generate(List.of(reading()));
        JsonNode items = schema.get("properties").get("questions").get("items");

        assertThat(items.has("oneOf")).isFalse();
        assertThat(items.get("properties").fieldNames().next()).isEqualTo("type");
        assertThat(items.get("properties").get("type").get("const").asText())
                .isEqualTo("ReadingComprehensionWithSingleChoiceHandler");
    }

    @org.junit.jupiter.api.Test
    void needsDistinctHandlers() {
        assertThatThrownBy(() -> TestDefinitionSchemaGenerator.generate(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TestDefinitionSchemaGenerator.generate(List.of(single(), single())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @org.junit.jupiter.api.Test
    void newKindsAreDescribed() {
        TestQuestionHandler<?> writing = new WritingQuestionFactory().createHandler(test, question);
        TestQuestionHandler<?> wordOrder = new WordOrderQuestionFactory().createHandler(test, question);

        assertThat(writing.getLlmSchema().get("properties").fieldNames()).toIterable()
                .containsExactlyInAnyOrder("instructions", "sampleAnswer");
        assertThat(wordOrder.getLlmSchema().get("properties").fieldNames()).toIterable()
                .containsExactlyInAnyOrder("instructions", "segments");
        assertThat(wordOrder.getLlmSchema().at("/properties/segments/minItems").asInt()).isEqualTo(2);
        assertThat(writing.getLlmTypeName()).isEqualTo("WritingQuestionHandler");
        assertThat(wordOrder.getLlmTypeName()).isEqualTo("WordOrderQuestionHandler");
    }

    @org.junit.jupiter.api.Test
    void germanKeepsTheGenericText() {
        ObjectNode withGerman = TestDefinitionSchemaGenerator.generate(List.of(single()), german);

        JsonNode choice = withGerman.at("/properties/questions/items/properties/choices/items");
        // (the description of the list is repeated on its items by the schema generator)
        JsonNode choiceWithoutDescription = ((ObjectNode) choice.deepCopy()).without("description");
        assertThat(choiceWithoutDescription).isEqualTo(StructuredOutputs.schemaOf(LanguageString.class));
        assertThat(choice.get("required")).extracting(JsonNode::asText).containsExactly("rawString");
        assertThat(german.getLanguageStringSchema()).isEqualTo(StructuredOutputs.schemaOf(LanguageString.class));
        assertThat(german.getLanguageStringInstructions()).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void japaneseRequiresFuriganaInEveryText() {
        ObjectNode schema = TestDefinitionSchemaGenerator.generate(List.of(single()), japanese);

        JsonNode choice = schema.at("/properties/questions/items/properties/choices/items");
        assertThat(choice.get("required")).extracting(JsonNode::asText).containsExactly("rawString", "metadata");
        assertThat(choice.at("/properties/metadata/items/properties/type/const").asText()).isEqualTo("furigana");
        // the instructions keep what the place says about them
        JsonNode instructions = schema.at("/properties/questions/items/properties/instructions");
        assertThat(instructions.get("description").asText()).startsWith("What the learner is asked to do")
                .contains("furigana");
        assertThat(instructions.get("required")).extracting(JsonNode::asText).contains("metadata");

        // a text without metadata is not valid any more, one with it is
        String choices = "\"choices\": [%s, {\"rawString\": \"b\", \"metadata\": []}], \"correctAnswer\": 0";
        String plain = "{\"rawString\": \"a\"}";
        String full = "{\"rawString\": \"a\", \"metadata\": []}";
        String instructionsJson = "\"instructions\": " + full;
        String bad = "{\"questions\": [{\"type\": \"SingleChoiceHandler\", " + instructionsJson + ", " + choices.formatted(plain) + "}]}";
        String good = "{\"questions\": [{\"type\": \"SingleChoiceHandler\", " + instructionsJson + ", " + choices.formatted(full) + "}]}";
        assertThat(StructuredOutputs.validate(schema, bad).valid()).isFalse();
        assertThat(StructuredOutputs.validate(schema, good).valid()).isTrue();
    }

    @org.junit.jupiter.api.Test
    void japaneseChecksEveryKanjiHasFurigana() {
        LanguageString covered = LanguageString.builder().append("日本語", "furigana", "にほんご").append("を").build();
        LanguageString partly = LanguageString.builder().append("食", "furigana", "た").append("べる").append("本").build();
        LanguageString plain = LanguageString.of("ひらがな and latin");

        assertThat(japanese.checkLanguageString(covered)).isEmpty();
        assertThat(japanese.checkLanguageString(plain)).isEmpty();
        assertThat(japanese.checkLanguageString(partly)).hasSize(1).first().asString().contains("本 (3)");
        // German does not care
        assertThat(german.checkLanguageString(partly)).isEmpty();
        assertThat(japanese.getLanguageStringInstructions()).contains("furigana").contains("kanji");
    }

    @org.junit.jupiter.api.Test
    void everyLanguageHasItsDefinition() {
        assertThat(japanese.getLanguage()).isEqualTo(Languages.JAPANESE);
        assertThat(german.getLanguage()).isEqualTo(Languages.GERMAN);
    }
}
