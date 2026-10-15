package com.github.enerccio.lingollm.domain;

import com.github.enerccio.lingollm.domain.collections.CEFR;
import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionGroup;
import com.github.enerccio.lingollm.domain.service.CancellationToken;
import com.github.enerccio.lingollm.domain.service.LanguageDefinitionService;
import com.github.enerccio.lingollm.domain.service.LanguageDefinitionService.TestCallback;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.github.enerccio.lingollm.domain.service.TestQuestionGroupService;
import com.github.enerccio.lingollm.domain.service.TestQuestionService;
import com.github.enerccio.lingollm.domain.service.TestService;
import com.github.enerccio.lingollm.domain.service.dto.TestQuestionRequest;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code LanguageDefinitionService.createGaugeTest}: the test is written by the model three questions at a time; the
 * prompts, the schemas, the batches that are asked again, and the entities made of the answers.
 */
class LevelGaugeTestTest extends LingoLLMTestBase {

    private static final String SINGLE = "SingleChoiceHandler";
    private static final String MULTIPLE = "MultipleChoiceHandler";
    private static final String READ_SINGLE = "ReadingComprehensionWithSingleChoiceHandler";
    private static final String READ_MULTIPLE = "ReadingComprehensionWithMultipleChoicesHandler";
    private static final String WRITING = "WritingQuestionHandler";
    private static final String WORD_ORDER = "WordOrderQuestionHandler";
    private static final String KANJI = "KanjiReadingHandler";

    /** The order the service asks for. */
    private static final List<String> TYPES = new ArrayList<>();

    static {
        TYPES.addAll(List.of(READ_SINGLE, READ_MULTIPLE, READ_SINGLE, READ_MULTIPLE, READ_SINGLE, READ_MULTIPLE));
        for (int i = 0; i < 3; i++) {
            TYPES.addAll(List.of(SINGLE, MULTIPLE, WRITING, WORD_ORDER, SINGLE));
        }
    }

    /** Japanese asks for 10 questions about the reading of a kanji after the others. */
    private static List<String> japaneseTypes() {
        List<String> types = new ArrayList<>(TYPES);
        for (int i = 0; i < 10; i++) {
            types.add(KANJI);
        }
        return types;
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

    private static class Collect implements TestCallback {
        final CompletableFuture<Test> test = new CompletableFuture<>();
        final List<String> progress = new CopyOnWriteArrayList<>();
        /** Cancels the generation when this many questions are done. */
        volatile int cancelAfter = -1;
        final CompletableFuture<CancellationToken> token = new CompletableFuture<>();

        public void onProgress(int done, int total) throws Exception {
            progress.add(done + "/" + total);
            if (done == cancelAfter) {
                token.get(5, TimeUnit.SECONDS).cancel();
            }
        }

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
            case KANJI -> {
                question.add("sentence", text("ねこがいる"));
                question.addProperty("targetKanji", "ねこ");
                question.add("choices", texts("ねこ", "いぬ", "とり"));
                question.addProperty("correctAnswer", 0);
            }
            case WRITING -> question.add("sampleAnswer", text("Ich heisse Anna."));
            case WORD_ORDER -> question.addProperty("sentence", "Ich | heisse|Anna|");
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

    /** How a kind of question is named in the schema the provider gets. */
    private static String constOf(String type) {
        return "\"const\":\"" + type + "\"";
    }

    private static List<JsonObject> questionsOf(List<String> types) {
        return new ArrayList<>(types.stream().map(LevelGaugeTestTest::question).toList());
    }

    /** The answers of the model: the questions grouped by their kind, three of one kind in each (as they are asked for). */
    private static List<String> batches(List<JsonObject> questions) {
        java.util.Map<String, List<JsonObject>> byKind = new java.util.LinkedHashMap<>();
        questions.forEach(q -> byKind.computeIfAbsent(q.get("type").getAsString(), type -> new ArrayList<>()).add(q));
        List<String> batches = new ArrayList<>();
        for (List<JsonObject> kind : byKind.values()) {
            for (int i = 0; i < kind.size(); i += 3) {
                batches.add(answer(kind.subList(i, Math.min(i + 3, kind.size()))));
            }
        }
        return batches;
    }

    private void enqueue(List<String> batches) {
        for (int i = 0; i < batches.size(); i++) {
            llm.enqueue(MockLLMResponse.text(batches.get(i)).withReasoning("Reasoning of the batch " + (i + 1)));
        }
    }

    private List<TestQuestion> questionsOf(Test test) throws Exception {
        return questionService.findByTest(test);
    }

    private Test make(LanguageDefinitionService service, Language profile, Collect collect) throws Exception {
        collect.token.complete(service.createGaugeTest(profile, createAI(), collect));
        return collect.await();
    }

    @org.junit.jupiter.api.Test
    void stitchesTheBatchesIntoOneTest() throws Exception {
        login();
        Language profile = profile(Languages.GERMAN);
        enqueue(batches(questionsOf(TYPES)));

        Collect collect = new Collect();
        Test test = make(german, profile, collect);

        assertThat(llm.getCompletionRequests()).hasSize(7);
        // questions done of all, from the start
        assertThat(collect.progress).containsExactly("0/21", "3/21", "6/21", "9/21", "12/21", "15/21", "18/21", "21/21");
        assertThat(test.getName()).isEqualTo("Level test");
        assertThat(test.getType()).isEqualTo(com.github.enerccio.lingollm.domain.collections.TestType.GRADE);
        assertThat(test.getReasoning()).startsWith("Questions 1, 3, 5:\nReasoning of the batch 1")
                .contains("\n\nQuestions 8, 13, 18:\nReasoning of the batch 5").endsWith("Questions 10, 15, 20:\nReasoning of the batch 7");
        assertThat(test.getLanguage().getId()).isEqualTo(profile.getId());

        List<TestQuestion> questions = questionsOf(test);
        assertThat(questions).hasSize(21);
        assertThat(questions).extracting(TestQuestion::getOrdinal).containsExactlyElementsOf(java.util.stream.IntStream.range(0, 21).boxed().toList());
        assertThat(questions).extracting(q -> q.getTestQuestionFactory().replaceAll(".*\\.", ""))
                .containsExactlyElementsOf(TYPES.stream().map(t -> t.replace("Handler", "Factory")).toList());

        // all in one group
        List<TestQuestionGroup> groups = groupService.findByTest(test);
        assertThat(groups).hasSize(1);
        assertThat(questions).allSatisfy(q -> assertThat(q.getQuestionGroup().getId()).isEqualTo(groups.getFirst().getId()));

        // the data of the questions is stored
        JsonObject first = questions.getFirst().getAttributes().getAsJsonObject("$testData");
        assertThat(first.getAsJsonObject("text").get("rawString").getAsString()).isEqualTo("Ein Text.");
        JsonObject wordOrder = questions.get(9).getAttributes().getAsJsonObject("$testData");
        assertThat(wordOrder.getAsJsonArray("segments")).hasSize(3);
    }

    @org.junit.jupiter.api.Test
    void everyRequestAsksForThreeQuestionsOfOneKindWithTheirLevelsAndTellsWhatIsWrittenAlready() throws Exception {
        login();
        Language profile = profile(Languages.GERMAN);
        enqueue(batches(questionsOf(TYPES)));

        make(german, profile, new Collect());

        List<String> prompts = llm.getCompletionRequests().stream().map(r -> r.getPromptText()).toList();
        // the first batch: the questions 1, 3 and 5 are all of the first kind, the handler of it tells what it is
        assertThat(prompts.getFirst()).contains("German").contains("exactly 3 questions").contains("\"number\" : 1")
                .contains("\"number\" : 3").contains("\"number\" : 5").doesNotContain("\"number\" : 2")
                .contains("\"section\" : \"reading\"").contains("\"level\" : \"A1\"").contains("- A1: Beginner.")
                .contains("The kind of question:").contains("- ReadingComprehensionWithSingleChoiceHandler: Pick one of the choices")
                .contains("\"text\" that comes with it").doesNotContain("WordOrderQuestionHandler").doesNotContain("Types of questions")
                .doesNotContain("{{").doesNotContain("The test has these questions already")
                .contains("\"level\" : \"B1\"").contains("\"level\" : \"C1\"").doesNotContain("\"level\" : \"A2\"")
                .doesNotContain("rejected");
        // the second one (the other reading kind) knows the first three
        assertThat(prompts.get(1)).contains("The test has these questions already")
                .contains("1. ReadingComprehensionWithSingleChoiceHandler: Choose | Ein Text.")
                .contains("5. ReadingComprehensionWithSingleChoiceHandler: Choose | Ein Text.")
                .doesNotContain("\n4. Reading").contains("\"number\" : 2").contains("Pick all the correct choices");
        // the levels grow, the last request is for the hardest
        assertThat(prompts.getLast()).contains("\"number\" : 20").contains("\"level\" : \"C2\"").contains("\"type\" : \"WordOrderQuestionHandler\"")
                .contains("\"sentence\" is one correct sentence cut into 3 to 8 parts");
        // exactly three questions of the one kind of the request are allowed, no choice between kinds
        String body = llm.getCompletionRequests().getFirst().getBody().toString();
        assertThat(body).contains("response_format").contains(constOf(READ_SINGLE)).doesNotContain(constOf(READ_MULTIPLE))
                .doesNotContain("oneOf").contains("\"minItems\":3").contains("\"maxItems\":3").doesNotContain(constOf(WORD_ORDER));
        assertThat(llm.getCompletionRequests().get(6).getBody().toString()).contains(constOf(WORD_ORDER)).doesNotContain(constOf(READ_SINGLE));
    }

    @org.junit.jupiter.api.Test
    void aBatchThatIsNotWhatWasAskedIsAskedAgainWithTheProblems() throws Exception {
        login();
        Language profile = profile(Languages.GERMAN);
        List<String> good = batches(questionsOf(TYPES));
        // the first question is of the other reading kind
        List<JsonObject> wrongKind = questionsOf(List.of(READ_MULTIPLE, READ_SINGLE, READ_SINGLE));
        llm.enqueue(MockLLMResponse.text(answer(wrongKind)));
        enqueue(good);

        Collect collect = new Collect();
        Test test = make(german, profile, collect);

        assertThat(questionsOf(test)).hasSize(21);
        assertThat(llm.getCompletionRequests()).hasSize(8);
        assertThat(llm.getCompletionRequests().get(0).getPromptText()).doesNotContain("rejected");
        assertThat(llm.getCompletionRequests().get(1).getPromptText()).contains("Your last answer was rejected")
                .contains("$.questions[0].type: must be the constant value '" + READ_SINGLE + "'").contains("\"number\" : 1");
        // the rejected answer is not counted as progress
        assertThat(collect.progress).hasSize(8).doesNotContain("0/21,0/21");
    }

    @org.junit.jupiter.api.Test
    void aBatchThatIsStillWrongAfterThreeAttemptsFailsTheTestAndSavesNothing() throws Exception {
        login();
        Language profile = profile(Languages.GERMAN);
        List<JsonObject> wrongKind = questionsOf(List.of(READ_MULTIPLE, READ_SINGLE, READ_SINGLE));
        for (int i = 0; i < 3; i++) {
            llm.enqueue(MockLLMResponse.text(answer(wrongKind)));
        }

        Collect collect = new Collect();
        assertThatThrownBy(() -> make(german, profile, collect)).hasCauseInstanceOf(StructuredOutputException.class)
                .hasMessageContaining("$.questions[0].type: must be the constant value '" + READ_SINGLE + "'");

        assertThat(llm.getCompletionRequests()).hasSize(3);
        assertThat(testService.findAllForUser()).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void aKindThatWasNotAskedForInTheBatchIsRejectedBySchema() throws Exception {
        login();
        Language profile = profile(Languages.GERMAN);
        List<JsonObject> wrongKinds = questionsOf(TYPES.subList(0, 3));
        wrongKinds.set(0, question(WRITING));
        for (int i = 0; i < 3; i++) {
            llm.enqueue(MockLLMResponse.text(batches(wrongKinds).getFirst()));
        }

        assertThatThrownBy(() -> make(german, profile, new Collect())).hasMessageContaining("must be the constant value");
        assertThat(llm.getCompletionRequests().get(1).getPromptText()).contains("Your last answer was rejected");
        assertThat(testService.findAllForUser()).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void aWrongNumberOfQuestionsInABatchIsRejected() throws Exception {
        login();
        Language profile = profile(Languages.GERMAN);
        for (int i = 0; i < 3; i++) {
            llm.enqueue(MockLLMResponse.text(answer(questionsOf(TYPES.subList(0, 2)))));
        }

        assertThatThrownBy(() -> make(german, profile, new Collect())).hasMessageContaining("must have at least 3 items but found 2");
        assertThat(testService.findAllForUser()).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void aCorrectAnswerOutsideTheChoicesIsRejected() throws Exception {
        login();
        Language profile = profile(Languages.GERMAN);
        List<JsonObject> questions = questionsOf(TYPES);
        questions.get(6).addProperty("correctAnswer", 7);
        List<String> batches = batches(questions);
        llm.enqueue(MockLLMResponse.text(batches.get(0)), MockLLMResponse.text(batches.get(1)));
        for (int i = 0; i < 3; i++) {
            llm.enqueue(MockLLMResponse.text(batches.get(2)));
        }

        assertThatThrownBy(() -> make(german, profile, new Collect())).hasMessageContaining("Question 7").hasMessageContaining("correct choice");
        assertThat(llm.getCompletionRequests()).hasSize(5);
        assertThat(testService.findAllForUser()).isEmpty();
    }

    private static final String WITH_KANJI = "日本語を選んでください";
    private static final String KANJI_READ = """
            {"texts": [{"readings": [{"word": "日本語", "reading": "にほんご"}, {"word": "選", "reading": "えら"}]}]}""";

    @org.junit.jupiter.api.Test
    void japaneseStillWithoutFuriganaAfterThreeAttemptsFailsTheTest() throws Exception {
        login();
        Language profile = profile(Languages.JAPANESE);
        List<JsonObject> questions = questionsOf(japaneseTypes());
        questions.get(7).add("instructions", text(WITH_KANJI));
        List<String> batches = batches(questions);
        // the question 8 is of the fifth batch (the kinds are written one after the other)
        for (int i = 0; i < 5; i++) {
            llm.enqueue(MockLLMResponse.text(batches.get(i)));
        }
        for (int i = 0; i < 3; i++) {
            llm.enqueue(MockLLMResponse.text("{\"texts\": [{\"readings\": [{\"word\": \"日本語\", \"reading\": \"にほんご\"}]}]}"));
        }

        assertThatThrownBy(() -> make(japanese, profile, new Collect()))
                .hasMessageContaining("Text 1").hasMessageContaining("no reading for 選");
        // the model was told what to fix, and was asked for the furigana only
        assertThat(llm.getCompletionRequests()).hasSize(8);
        assertThat(llm.getCompletionRequests().get(6).getPromptText()).contains("Your last answer was rejected")
                .contains("no reading for 選");
        assertThat(testService.findAllForUser()).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void japaneseQuestionsArePlainAndTheFuriganaIsAPassOfItsOwnAfterTheBatch() throws Exception {
        login();
        Language profile = profile(Languages.JAPANESE);
        List<JsonObject> questions = questionsOf(japaneseTypes());
        questions.get(7).add("instructions", text(WITH_KANJI));
        // a sentence with the target in it, the model first splits the target
        questions.get(21).add("sentence", text("今日の天気はいいです。"));
        questions.get(21).addProperty("targetKanji", "天気");
        List<String> batches = batches(questions);
        // the kinds are written one after the other: the question 8 is in the fifth batch, the kanji questions in the last four
        for (int i = 0; i < 5; i++) {
            llm.enqueue(MockLLMResponse.text(batches.get(i)));
        }
        llm.enqueue(MockLLMResponse.text(KANJI_READ));
        for (int i = 5; i < 7; i++) {
            llm.enqueue(MockLLMResponse.text(batches.get(i)));
        }
        llm.enqueue(MockLLMResponse.text(batches.get(7)));
        llm.enqueue(MockLLMResponse.text("""
                {"texts": [{"readings": [{"word": "今日", "reading": "きょう"}, {"word": "天", "reading": "てん"},
                {"word": "気", "reading": "き"}]}]}"""));
        llm.enqueue(MockLLMResponse.text("""
                {"texts": [{"readings": [{"word": "今日", "reading": "きょう"}, {"word": "天気", "reading": "てんき"}]}]}"""));
        for (int i = 8; i < batches.size(); i++) {
            llm.enqueue(MockLLMResponse.text(batches.get(i)));
        }

        Collect collect = new Collect();
        Test test = make(japanese, profile, collect);

        List<TestQuestion> made = questionsOf(test);
        assertThat(made).hasSize(31);
        assertThat(made.subList(21, 31)).allSatisfy(q -> assertThat(q.getTestQuestionFactory()).endsWith("KanjiReadingFactory"));
        // the 11 batches, the pass over the batch with the kanji question and two over the sentence
        assertThat(llm.getCompletionRequests()).hasSize(14);
        // progress counts the accepted batches only (and the start), not the passes over the texts
        assertThat(collect.progress).hasSize(12);
        // the questions ask for plain texts and for no furigana
        String first = llm.getCompletionRequests().getFirst().getPromptText();
        assertThat(first).contains("never romaji").contains("Write plain text only");
        String firstBody = llm.getCompletionRequests().getFirst().getBody().toString();
        assertThat(firstBody).doesNotContain("\"parts\"").doesNotContain("\"reading\"");
        // the pass over the texts: only the texts with kanji, the schema wants the parts, one text each
        String pass = llm.getCompletionRequests().get(5).getPromptText();
        assertThat(pass).contains("furigana").contains("1. " + WITH_KANJI).doesNotContain("\n2. ").doesNotContain("{{");
        assertThat(llm.getCompletionRequests().get(5).getBody().toString()).contains("\"readings\"").contains("\"reading\"")
                .contains("\"minItems\":1").contains("\"maxItems\":1");
        // and what went wrong is told to the model, with the words that must not be split
        assertThat(pass).doesNotContain("never split");
        assertThat(llm.getCompletionRequests().get(9).getPromptText()).contains("never split").contains("天気");
        assertThat(llm.getCompletionRequests().get(10).getPromptText()).contains("Your last answer was rejected").contains("\"天気\" must be one word");

        // the furigana is in the saved texts
        JsonObject instructions = made.get(7).getAttributes().getAsJsonObject("$testData").getAsJsonObject("instructions");
        assertThat(instructions.get("rawString").getAsString()).isEqualTo(WITH_KANJI);
        assertThat(instructions.getAsJsonArray("metadata")).hasSize(2);
        assertThat(instructions.getAsJsonArray("metadata").get(1).getAsJsonObject().get("start").getAsInt()).isEqualTo(4);
        JsonObject sentence = made.get(21).getAttributes().getAsJsonObject("$testData").getAsJsonObject("sentence");
        assertThat(sentence.getAsJsonArray("metadata").toString()).contains("\"value\":\"てんき\"").contains("\"start\":3,\"end\":5");
        // the kanji questions: levels from A1 to C2, the kanji of the matching JLPT level
        String kanji = llm.getCompletionRequests().stream().skip(8).map(r -> r.getPromptText()).reduce("", String::concat);
        assertThat(kanji).contains("\"section\" : \"kanji\"").contains("JLPT level N5").contains("JLPT level N4")
                .contains("JLPT level N3").contains("JLPT level N2").contains("JLPT level N1").contains("\"level\" : \"C2\"");
        assertThat(llm.getCompletionRequests().get(8).getBody().toString()).contains(KANJI).contains("targetKanji");
    }

    @org.junit.jupiter.api.Test
    void aKanjiQuestionNeedsItsTargetInTheSentence() throws Exception {
        login();
        Language profile = profile(Languages.JAPANESE);
        List<JsonObject> questions = questionsOf(japaneseTypes());
        questions.get(25).addProperty("targetKanji", "犬");
        List<String> batches = batches(questions);
        for (int i = 0; i < 8; i++) {
            llm.enqueue(MockLLMResponse.text(batches.get(i)));
        }
        for (int i = 0; i < 3; i++) {
            llm.enqueue(MockLLMResponse.text(batches.get(8)));
        }

        assertThatThrownBy(() -> make(japanese, profile, new Collect()))
                .hasMessageContaining("Question 26").hasMessageContaining("not in the sentence");
        assertThat(testService.findAllForUser()).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void theProfileMustBeOfTheLanguage() throws Exception {
        login();
        Language profile = profile(Languages.GERMAN);
        assertThatThrownBy(() -> japanese.createGaugeTest(profile, createAI(), new Collect()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @org.junit.jupiter.api.Test
    void aFailureOfTheProviderEndsTheTestWithoutAskingAgain() throws Exception {
        login();
        Language profile = profile(Languages.GERMAN);
        llm.enqueue(MockLLMResponse.error(500, "down"));

        Collect collect = new Collect();
        assertThatThrownBy(() -> make(german, profile, collect)).hasCauseInstanceOf(Exception.class);

        // the failure is not a wrong answer: no batch is asked again by the service
        assertThat(testService.findAllForUser()).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void cancellingBetweenBatchesStopsTheGeneration() throws Exception {
        login();
        Language profile = profile(Languages.GERMAN);
        enqueue(batches(questionsOf(TYPES)));

        Collect collect = new Collect();
        collect.cancelAfter = 3;
        assertThatThrownBy(() -> make(german, profile, collect)).isInstanceOf(java.util.concurrent.CancellationException.class);

        assertThat(collect.progress).containsExactly("0/21", "3/21");
        assertThat(testService.findAllForUser()).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void anyTestCanBeAskedForWithItsOwnQuestions() throws Exception {
        login();
        Language profile = profile(Languages.GERMAN);
        TestQuestionRequest one = new TestQuestionRequest("grammar", WRITING, "about the articles");
        one.setLevel(CEFR.B2);
        TestQuestionRequest two = new TestQuestionRequest("grammar", WORD_ORDER, null);
        two.setLevel(CEFR.A1);
        // a request is of one kind
        llm.enqueue(MockLLMResponse.text(answer(List.of(question(WRITING)))), MockLLMResponse.text(answer(List.of(question(WORD_ORDER)))));

        Collect collect = new Collect();
        collect.token.complete(german.createTest(profile, createAI(), "Articles", List.of(one, two), collect));
        Test test = collect.await();

        assertThat(test.getName()).isEqualTo("Articles");
        assertThat(test.getType()).isEqualTo(com.github.enerccio.lingollm.domain.collections.TestType.REGULAR);
        assertThat(questionsOf(test)).hasSize(2);
        assertThat(llm.getCompletionRequests()).hasSize(2);
        assertThat(llm.getCompletionRequests().getFirst().getPromptText()).contains("exactly 1 questions").contains("about the articles")
                .contains("\"level\" : \"B2\"");
        assertThat(questionsOf(test)).extracting(q -> q.getTestQuestionFactory().replaceAll(".*\\.", ""))
                .containsExactly("WritingQuestionFactory", "WordOrderQuestionFactory");
    }
}
