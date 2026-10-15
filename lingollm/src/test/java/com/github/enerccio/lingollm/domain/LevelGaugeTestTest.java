package com.github.enerccio.lingollm.domain;

import com.github.enerccio.lingollm.domain.collections.CEFR;
import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionGroup;
import com.github.enerccio.lingollm.domain.service.LanguageDefinitionService;
import com.github.enerccio.lingollm.domain.service.LanguageDefinitionService.GaugeTestCallback;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.github.enerccio.lingollm.domain.service.TestQuestionGroupService;
import com.github.enerccio.lingollm.domain.service.TestQuestionService;
import com.github.enerccio.lingollm.domain.service.TestService;
import com.github.enerccio.lingollm.domain.service.structured.StructuredOutputException;
import com.github.enerccio.lingollm.test.LingoLLMTestBase;
import com.github.enerccio.lingollm.test.llm.MockLLMResponse;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code LanguageDefinitionService.createGaugeTest}: the prompt, the schema, and the entities made of the answer.
 */
class LevelGaugeTestTest extends LingoLLMTestBase {

    private static final String SINGLE = "SingleChoiceHandler";
    private static final String MULTIPLE = "MultipleChoiceHandler";
    private static final String READ_SINGLE = "ReadingComprehensionWithSingleChoiceHandler";
    private static final String READ_MULTIPLE = "ReadingComprehensionWithMultipleChoicesHandler";
    private static final String WRITING = "WritingQuestionHandler";
    private static final String WORD_ORDER = "WordOrderQuestionHandler";

    /** The order the service asks for. */
    private static final List<String> TYPES = new ArrayList<>();

    static {
        TYPES.addAll(List.of(READ_SINGLE, READ_MULTIPLE, READ_SINGLE, READ_MULTIPLE, READ_SINGLE));
        for (int i = 0; i < 3; i++) {
            TYPES.addAll(List.of(SINGLE, MULTIPLE, WRITING, WORD_ORDER, SINGLE));
        }
    }

    @Autowired
    @Qualifier("germanLanguageDefinitionService")
    private LanguageDefinitionService german;

    @Autowired
    @Qualifier("japaneseLanguageDefinitionService")
    private LanguageDefinitionService japanese;

    @Autowired
    private LanguageService languageService;

    @Autowired
    private TestService testService;

    @Autowired
    private TestQuestionService questionService;

    @Autowired
    private TestQuestionGroupService groupService;

    private static class Collect implements GaugeTestCallback {
        final CompletableFuture<Test> test = new CompletableFuture<>();

        public void onTest(Test t) {
            test.complete(t);
        }

        public void onError(Throwable error) {
            test.completeExceptionally(error);
        }

        public void onCancel() {
            test.cancel(false);
        }

        Test await() throws Exception {
            return test.get(10, TimeUnit.SECONDS);
        }
    }

    private Language profile(Languages template) throws Exception {
        Language language = new Language();
        language.setLanguage(template);
        language.setUserGrade(CEFR.A2);
        language.setUserGradeFineDetails("knows the hotel phrases");
        return languageService.save(language);
    }

    private static JsonObject text(String raw) {
        JsonObject text = new JsonObject();
        text.addProperty("rawString", raw);
        return text;
    }

    private static JsonArray texts(String... raws) {
        JsonArray array = new JsonArray();
        for (String raw : raws) {
            array.add(text(raw));
        }
        return array;
    }

    private static JsonObject question(String type) {
        JsonObject question = new JsonObject();
        question.addProperty("type", type);
        question.add("instructions", text("Choose"));
        switch (type) {
            case SINGLE, READ_SINGLE -> {
                question.add("choices", texts("a", "b", "c"));
                question.addProperty("correctAnswer", 1);
            }
            case MULTIPLE, READ_MULTIPLE -> {
                question.add("choices", texts("a", "b", "c"));
                JsonArray correct = new JsonArray();
                correct.add(0);
                correct.add(2);
                question.add("correctAnswers", correct);
            }
            case WRITING -> question.add("sampleAnswer", text("Ich heisse Anna."));
            case WORD_ORDER -> question.add("segments", texts("Ich", "heisse", "Anna"));
            default -> throw new IllegalArgumentException(type);
        }
        if (type.startsWith("ReadingComprehension")) {
            question.add("text", text("Ein Text."));
        }
        return question;
    }

    private static String answer(List<JsonObject> questions) {
        JsonArray array = new JsonArray();
        questions.forEach(array::add);
        JsonObject answer = new JsonObject();
        answer.add("questions", array);
        return answer.toString();
    }

    /** Japanese asks for the metadata of every text, even when it is empty. */
    private static JsonObject withMetadata(JsonObject answer) {
        addMissingMetadata(answer);
        return answer;
    }

    private static void addMissingMetadata(com.google.gson.JsonElement element) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(LevelGaugeTestTest::addMissingMetadata);
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("rawString") && !object.has("metadata")) {
                object.add("metadata", new JsonArray());
            }
            object.entrySet().forEach(entry -> addMissingMetadata(entry.getValue()));
        }
    }

    private static String validAnswer() {
        return answer(TYPES.stream().map(LevelGaugeTestTest::question).toList());
    }

    private List<TestQuestion> questionsOf(Test test) throws Exception {
        return questionService.findByTest(test);
    }

    @org.junit.jupiter.api.Test
    void makesTheTestOfTheAnswer() throws Exception {
        login();
        Language profile = profile(Languages.GERMAN);
        AI ai = createAI();
        llm.enqueue(MockLLMResponse.text(validAnswer()).withReasoning("Start with easy ones."));

        Collect collect = new Collect();
        german.createGaugeTest(profile, ai, collect);
        Test test = collect.await();

        assertThat(test.getName()).isEqualTo("Level test");
        assertThat(test.getReasoning()).isEqualTo("Start with easy ones.");
        assertThat(test.getLanguage().getId()).isEqualTo(profile.getId());

        List<TestQuestion> questions = questionsOf(test);
        assertThat(questions).hasSize(20);
        assertThat(questions).extracting(TestQuestion::getOrdinal).containsExactlyElementsOf(java.util.stream.IntStream.range(0, 20).boxed().toList());
        assertThat(questions).extracting(q -> q.getTestQuestionFactory().replaceAll(".*\\.", ""))
                .containsExactlyElementsOf(TYPES.stream().map(t -> t.replace("Handler", "Factory")
                        .replace("ReadingComprehensionWithMultipleChoicesFactory", "ReadingComprehensionWithMultipleChoicesFactory")).toList());

        // all in one group
        List<TestQuestionGroup> groups = groupService.findByTest(test);
        assertThat(groups).hasSize(1);
        assertThat(questions).allSatisfy(q -> assertThat(q.getQuestionGroup().getId()).isEqualTo(groups.getFirst().getId()));

        // the data of the questions is stored
        JsonObject first = questions.getFirst().getAttributes().getAsJsonObject("$testData");
        assertThat(first.getAsJsonObject("text").get("rawString").getAsString()).isEqualTo("Ein Text.");
        JsonObject wordOrder = questions.get(8).getAttributes().getAsJsonObject("$testData");
        assertThat(wordOrder.getAsJsonArray("segments")).hasSize(3);
    }

    @org.junit.jupiter.api.Test
    void promptAndSchemaDescribeTheRequest() throws Exception {
        login();
        Language profile = profile(Languages.GERMAN);
        llm.enqueue(MockLLMResponse.text(validAnswer()));

        Collect collect = new Collect();
        german.createGaugeTest(profile, createAI(), collect);
        collect.await();

        var request = llm.lastCompletionRequest();
        String prompt = request.getPromptText();
        assertThat(prompt).contains("German").contains("A2").contains("knows the hotel phrases")
                .contains("exactly 20 questions").contains("\"type\" : \"WordOrderQuestionHandler\"")
                .contains("\"number\" : 20").contains("\"section\" : \"reading\"")
                .doesNotContain("{{");
        // all six kinds are in the schema the provider gets
        String body = request.getBody().toString();
        assertThat(body).contains("response_format")
                .contains(READ_SINGLE).contains(READ_MULTIPLE).contains(SINGLE).contains(MULTIPLE)
                .contains(WRITING).contains(WORD_ORDER);
    }

    @org.junit.jupiter.api.Test
    void aWrongOrderOfKindsSavesNothing() throws Exception {
        login();
        Language profile = profile(Languages.GERMAN);
        List<String> types = new ArrayList<>(TYPES);
        types.set(0, SINGLE);
        llm.enqueue(MockLLMResponse.text(answer(types.stream().map(LevelGaugeTestTest::question).toList())));

        Collect collect = new Collect();
        german.createGaugeTest(profile, createAI(), collect);

        assertThatThrownBy(collect::await).hasCauseInstanceOf(StructuredOutputException.class)
                .hasMessageContaining("Question 1: should be " + READ_SINGLE);
        assertThat(testService.findAllForUser()).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void aWrongNumberOfQuestionsSavesNothing() throws Exception {
        login();
        Language profile = profile(Languages.GERMAN);
        llm.enqueue(MockLLMResponse.text(answer(TYPES.subList(0, 19).stream().map(LevelGaugeTestTest::question).toList())));

        Collect collect = new Collect();
        german.createGaugeTest(profile, createAI(), collect);

        assertThatThrownBy(collect::await).hasMessageContaining("19 questions, 20 were asked for");
        assertThat(testService.findAllForUser()).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void aCorrectAnswerOutsideTheChoicesSavesNothing() throws Exception {
        login();
        Language profile = profile(Languages.GERMAN);
        List<JsonObject> questions = new ArrayList<>(TYPES.stream().map(LevelGaugeTestTest::question).toList());
        questions.get(5).addProperty("correctAnswer", 7);
        llm.enqueue(MockLLMResponse.text(answer(questions)));

        Collect collect = new Collect();
        german.createGaugeTest(profile, createAI(), collect);

        assertThatThrownBy(collect::await).hasMessageContaining("Question 6").hasMessageContaining("correct choice");
        assertThat(testService.findAllForUser()).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void japaneseNeedsFuriganaOverEveryKanji() throws Exception {
        login();
        Language profile = profile(Languages.JAPANESE);
        List<JsonObject> questions = new ArrayList<>(TYPES.stream().map(LevelGaugeTestTest::question).toList());
        questions.get(7).add("instructions", text("日本語を選んでください"));
        llm.enqueue(MockLLMResponse.text(withMetadata(JsonParser.parseString(answer(questions)).getAsJsonObject()).toString()));

        Collect collect = new Collect();
        japanese.createGaugeTest(profile, createAI(), collect);

        assertThatThrownBy(collect::await).hasMessageContaining("Question 8").hasMessageContaining("no furigana over 日 (0)");
        assertThat(testService.findAllForUser()).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void japaneseAcceptsFuriganaOverTheKanji() throws Exception {
        login();
        Language profile = profile(Languages.JAPANESE);
        List<JsonObject> questions = new ArrayList<>(TYPES.stream().map(LevelGaugeTestTest::question).toList());
        JsonObject withFurigana = text("日本語を選んでください");
        JsonArray metadata = new JsonArray();
        for (Object[] f : new Object[][]{{0, 3, "にほんご"}, {4, 5, "えら"}}) {
            JsonObject entry = new JsonObject();
            entry.addProperty("type", "furigana");
            entry.addProperty("start", (Integer) f[0]);
            entry.addProperty("end", (Integer) f[1]);
            entry.addProperty("value", (String) f[2]);
            metadata.add(entry);
        }
        withFurigana.add("metadata", metadata);
        questions.get(7).add("instructions", withFurigana);
        llm.enqueue(MockLLMResponse.text(withMetadata(JsonParser.parseString(answer(questions)).getAsJsonObject()).toString()));

        Collect collect = new Collect();
        japanese.createGaugeTest(profile, createAI(), collect);
        Test test = collect.await();

        assertThat(questionsOf(test)).hasSize(20);
        // the prompt tells the model about furigana and the schema requires them
        assertThat(llm.lastCompletionRequest().getPromptText()).contains("furigana").contains("never romaji");
        assertThat(llm.lastCompletionRequest().getBody().toString()).contains("\"const\":\"furigana\"");
    }

    @org.junit.jupiter.api.Test
    void theProfileMustBeOfTheLanguage() throws Exception {
        login();
        Language profile = profile(Languages.GERMAN);
        assertThatThrownBy(() -> japanese.createGaugeTest(profile, createAI(), new Collect()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @org.junit.jupiter.api.Test
    void failureOfTheProviderGoesToTheCallback() throws Exception {
        login();
        Language profile = profile(Languages.GERMAN);
        llm.enqueue(MockLLMResponse.error(500, "down"));

        Collect collect = new Collect();
        german.createGaugeTest(profile, createAI(), collect);

        assertThatThrownBy(collect::await).hasCauseInstanceOf(Exception.class);
        assertThat(testService.findAllForUser()).isEmpty();
    }
}
