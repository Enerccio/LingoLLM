package com.github.enerccio.lingollm.domain;

import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.factories.TestQuestionFactory;
import com.github.enerccio.lingollm.domain.factories.impl.MultipleChoiceFactory;
import com.github.enerccio.lingollm.domain.factories.impl.ReadingComprehensionWithMultipleChoicesFactory;
import com.github.enerccio.lingollm.domain.factories.impl.ReadingComprehensionWithSingleChoiceFactory;
import com.github.enerccio.lingollm.domain.factories.impl.SingleChoiceFactory;
import com.github.enerccio.lingollm.domain.factories.impl.WordOrderQuestionFactory;
import com.github.enerccio.lingollm.domain.factories.impl.WritingQuestionFactory;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.github.enerccio.lingollm.domain.service.TestQuestionFactoryRegistry;
import com.github.enerccio.lingollm.domain.service.TestQuestionService;
import com.github.enerccio.lingollm.domain.service.TestService;
import com.github.enerccio.lingollm.test.LingoLLMTestBase;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionHandler;
import com.github.enerccio.lingollm.ui.lang.languages.common.impl.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestQuestionHandlersTest extends LingoLLMTestBase {

    @Autowired
    private TestQuestionFactoryRegistry registry;

    @Autowired
    private TestService testService;

    @Autowired
    private TestQuestionService questionService;

    @Autowired
    private LanguageService languageService;

    private Test test;

    @BeforeEach
    void testWithProfile() throws Exception {
        login();
        Language language = new Language();
        language.setLanguage(Languages.JAPANESE);
        Test t = new Test();
        t.setLanguage(languageService.save(language));
        test = testService.save(t);
    }

    private static LanguageString text(String raw) {
        return LanguageString.of(raw);
    }

    private TestQuestion question(Class<? extends TestQuestionFactory> factory) throws Exception {
        TestQuestion question = new TestQuestion();
        question.setLanguage(test.getLanguage());
        question.setParentTest(test);
        question.setTestQuestionFactory(factory.getName());
        return questionService.save(question);
    }

    private TestQuestionHandler<?> handler(TestQuestion question) {
        return registry.getFactory(question).createHandler(test, question);
    }

    private JsonObject savedData(TestQuestion question) throws Exception {
        return questionService.find(question.getId()).getAttributes().getAsJsonObject("$testData");
    }

    @org.junit.jupiter.api.Test
    void multipleChoiceKeepsTheCorrectChoices() throws Exception {
        TestQuestion question = question(MultipleChoiceFactory.class);

        MultipleChoiceHandler<?> handler = (MultipleChoiceHandler<?>) handler(question);
        handler.setInstructions(text("Pick the verbs"));
        handler.setChoices(List.of(text("hon"), text("taberu"), text("miru")), List.of(2, 1));
        handler.save();

        JsonObject data = savedData(question);
        assertThat(data.getAsJsonObject("instructions").get("rawString").getAsString()).isEqualTo("Pick the verbs");
        assertThat(data.getAsJsonArray("choices")).hasSize(3);
        assertThat(data.getAsJsonArray("correctAnswers").toString()).isEqualTo("[1,2]");
    }

    @org.junit.jupiter.api.Test
    void writingQuestionKeepsTheSampleAnswer() throws Exception {
        TestQuestion question = question(WritingQuestionFactory.class);

        WritingQuestionHandler handler = (WritingQuestionHandler) handler(question);
        handler.setInstructions(text("Introduce yourself"));
        handler.setSampleAnswer(text("Watashi wa Anna desu"));
        handler.save();

        JsonObject data = savedData(question);
        assertThat(data.getAsJsonObject("sampleAnswer").get("rawString").getAsString()).isEqualTo("Watashi wa Anna desu");
        assertThat(data.getAsJsonObject("instructions").get("rawString").getAsString()).isEqualTo("Introduce yourself");
    }

    @org.junit.jupiter.api.Test
    void wordOrderQuestionKeepsTheSegmentsInOrder() throws Exception {
        TestQuestion question = question(WordOrderQuestionFactory.class);

        WordOrderQuestionHandler handler = (WordOrderQuestionHandler) handler(question);
        handler.setSegments(List.of(text("Ich"), text("heisse"), text("Anna")));
        handler.save();

        assertThat(savedData(question).getAsJsonArray("segments").toString())
                .isEqualTo("[{\"rawString\":\"Ich\",\"metadata\":[]},{\"rawString\":\"heisse\",\"metadata\":[]},{\"rawString\":\"Anna\",\"metadata\":[]}]");
        assertThatThrownBy(() -> handler.setSegments(List.of(text("Ich")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.setSegments(List.of(text("Ich"), text(" ")))).isInstanceOf(IllegalArgumentException.class);
    }

    @org.junit.jupiter.api.Test
    void dataFromTheModelIsReadAndChecked() throws Exception {
        TestQuestion question = question(SingleChoiceFactory.class);
        TestQuestionHandler<?> handler = handler(question);

        handler.setLlmData(JsonParser.parseString("""
                {"instructions": {"rawString": "Pick"}, "choices": [{"rawString": "a"}, {"rawString": "b"}], "correctAnswer": 1}""")
                .getAsJsonObject());
        handler.save();
        assertThat(savedData(question).get("correctAnswer").getAsInt()).isEqualTo(1);

        assertThatThrownBy(() -> handler.setLlmData(JsonParser.parseString("""
                {"choices": [{"rawString": "a"}, {"rawString": "b"}], "correctAnswer": 2}""").getAsJsonObject()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("correct choice");
        assertThatThrownBy(() -> handler.setLlmData(JsonParser.parseString("""
                {"choices": "not a list", "correctAnswer": 0}""").getAsJsonObject()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("can not be read");

        TestQuestionHandler<?> multiple = handler(question(MultipleChoiceFactory.class));
        assertThatThrownBy(() -> multiple.setLlmData(JsonParser.parseString("""
                {"choices": [{"rawString": "a"}, {"rawString": "b"}], "correctAnswers": [0, 0]}""").getAsJsonObject()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("only once");
    }

    @org.junit.jupiter.api.Test
    void multipleChoiceMayHaveNoCorrectChoice() throws Exception {
        TestQuestion question = question(MultipleChoiceFactory.class);

        MultipleChoiceHandler<?> handler = (MultipleChoiceHandler<?>) handler(question);
        handler.setChoices(List.of(text("hon"), text("neko")), List.of());
        handler.save();

        assertThat(savedData(question).getAsJsonArray("correctAnswers")).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void choicesOutsideTheListAreRefused() throws Exception {
        MultipleChoiceHandler<?> multiple = (MultipleChoiceHandler<?>) handler(question(MultipleChoiceFactory.class));
        SingleChoiceHandler<?> single = (SingleChoiceHandler<?>) handler(question(SingleChoiceFactory.class));
        List<LanguageString> choices = List.of(text("a"), text("b"));

        assertThatThrownBy(() -> multiple.setChoices(choices, List.of(2))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> multiple.setChoices(choices, List.of(-1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> multiple.setChoices(choices, List.of(1, 1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> single.setChoices(choices, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> single.setChoices(List.of(), 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @org.junit.jupiter.api.Test
    void readingComprehensionWithSingleChoiceAddsTheText() throws Exception {
        TestQuestion question = question(ReadingComprehensionWithSingleChoiceFactory.class);

        TestQuestionHandler<?> created = handler(question);
        assertThat(created).isInstanceOf(ReadingComprehensionWithSingleChoiceHandler.class);
        ReadingComprehensionWithSingleChoiceHandler handler = (ReadingComprehensionWithSingleChoiceHandler) created;
        handler.setText(text("Taro ate an apple."));
        handler.setChoices(List.of(text("apple"), text("pear")), 0);
        handler.save();

        JsonObject data = savedData(question);
        assertThat(data.getAsJsonObject("text").get("rawString").getAsString()).isEqualTo("Taro ate an apple.");
        assertThat(data.get("correctAnswer").getAsInt()).isZero();
        assertThat(data.getAsJsonArray("choices")).hasSize(2);
    }

    @org.junit.jupiter.api.Test
    void readingComprehensionWithMultipleChoicesAddsTheText() throws Exception {
        TestQuestion question = question(ReadingComprehensionWithMultipleChoicesFactory.class);

        TestQuestionHandler<?> created = handler(question);
        assertThat(created).isInstanceOf(ReadingComprehensionWithMultipleChoicesHandler.class);
        ReadingComprehensionWithMultipleChoicesHandler handler = (ReadingComprehensionWithMultipleChoicesHandler) created;
        handler.setText(text("Taro ate an apple and a pear."));
        handler.setChoices(List.of(text("apple"), text("pear"), text("fish")), List.of(0, 1));
        handler.save();

        JsonObject data = savedData(question);
        assertThat(data.getAsJsonObject("text").get("rawString").getAsString()).isEqualTo("Taro ate an apple and a pear.");
        assertThat(data.getAsJsonArray("correctAnswers").toString()).isEqualTo("[0,1]");
    }

    @org.junit.jupiter.api.Test
    void textAndChoicesCanBeSetInAnyOrderAndChangedLater() throws Exception {
        TestQuestion question = question(ReadingComprehensionWithSingleChoiceFactory.class);
        ReadingComprehensionWithSingleChoiceHandler handler = (ReadingComprehensionWithSingleChoiceHandler) handler(question);

        handler.setChoices(List.of(text("a"), text("b")), 1);
        handler.save();
        handler.setText(text("first"));
        handler.save();
        handler.setText(text("second"));
        handler.save();

        JsonObject data = savedData(question);
        assertThat(data.getAsJsonObject("text").get("rawString").getAsString()).isEqualTo("second");
        assertThat(data.get("correctAnswer").getAsInt()).isOne();
    }
}
