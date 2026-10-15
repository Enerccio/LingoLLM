package com.github.enerccio.lingollm.domain.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.enerccio.lingollm.domain.collections.CEFR;
import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.collections.TestType;
import com.github.enerccio.lingollm.domain.factories.TestQuestionFactory;
import com.github.enerccio.lingollm.domain.factories.impl.MultipleChoiceFactory;
import com.github.enerccio.lingollm.domain.factories.impl.PlainGroupFactory;
import com.github.enerccio.lingollm.domain.factories.impl.ReadingComprehensionWithMultipleChoicesFactory;
import com.github.enerccio.lingollm.domain.factories.impl.ReadingComprehensionWithSingleChoiceFactory;
import com.github.enerccio.lingollm.domain.factories.impl.SingleChoiceFactory;
import com.github.enerccio.lingollm.domain.factories.impl.WordOrderQuestionFactory;
import com.github.enerccio.lingollm.domain.factories.impl.WritingQuestionFactory;
import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionGroup;
import com.github.enerccio.lingollm.domain.service.CancellationToken;
import com.github.enerccio.lingollm.domain.service.InferenceService;
import com.github.enerccio.lingollm.domain.service.InferenceService.InferenceResultCallback;
import com.github.enerccio.lingollm.domain.service.InferenceServices;
import com.github.enerccio.lingollm.domain.service.LanguageDefinitionService;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.github.enerccio.lingollm.domain.service.TemplateService;
import com.github.enerccio.lingollm.domain.service.TestQuestionGroupService;
import com.github.enerccio.lingollm.domain.service.TestQuestionService;
import com.github.enerccio.lingollm.domain.service.TestService;
import com.github.enerccio.lingollm.domain.service.dto.LLMChatMessage;
import com.github.enerccio.lingollm.domain.service.dto.LLMRole;
import com.github.enerccio.lingollm.domain.service.dto.TestQuestionRequest;
import com.github.enerccio.lingollm.domain.service.structured.SchemaCheck;
import com.github.enerccio.lingollm.domain.service.structured.StructuredOutputException;
import com.github.enerccio.lingollm.domain.service.structured.StructuredOutputs;
import com.github.enerccio.lingollm.domain.service.structured.TestDefinitionSchemaGenerator;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.ui.components.ThreadCopyRequestAttributes;
import com.github.enerccio.lingollm.ui.components.ThreadCopyRequestAttributes.InRequestScope;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageStrings;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionHandler;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base of the definitions of the languages: keeps the language and holds what all of them share, above all the
 * writing of tests by the model.
 * <p>
 * A test is written in phases: the questions asked for are grouped by their kind and cut into batches of at most
 * {@value #BATCH_SIZE} of one kind, and the model writes one batch at a time, so a weaker model has a short answer to get
 * right and a schema of one kind (a schema that offers several is not kept apart by every provider). Every question asked for has the CEFR
 * level it is written for. A batch that is not what was asked is asked again (with what was wrong), up to
 * {@value #MAX_ATTEMPTS} times; the accepted batches are stitched into one test at the end.
 * <p>
 * The level gauging test has {@value #READING_QUESTIONS} questions about a text ({@link #SECTION_READING}) and
 * {@value #OTHER_QUESTIONS} others ({@link #SECTION_OTHER}), all in one group, the levels of each section spread evenly
 * over A1-C2. A language can add questions between the sections ({@link #questionsBetweenSections}) and at the end
 * ({@link #questionsAtEnd}), and a different kind of question needs only {@link #questionKinds}.
 */
public abstract class BaseLanguageDefinitionService implements LanguageDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(BaseLanguageDefinitionService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    public static final String SECTION_READING = "reading";
    public static final String SECTION_OTHER = "other";
    public static final int READING_QUESTIONS = 6;
    public static final int OTHER_QUESTIONS = 15;
    /** How many questions the model writes in one request. */
    public static final int BATCH_SIZE = 3;
    /** The list of the texts in the answer of the second pass. */
    private static final String TEXTS = "texts";
    /** How many times the model is asked for the same batch before the test fails. */
    public static final int MAX_ATTEMPTS = 3;

    /** The longest text of a written question repeated to the model in the next requests. */
    private static final int SUMMARY_LENGTH = 100;

    private final Languages language;

    @Autowired
    private LanguageService languageService;

    @Autowired
    private TemplateService templateService;

    @Autowired
    private InferenceServices inferenceServices;

    @Autowired
    private TestService testService;

    @Autowired
    private TestQuestionGroupService groupService;

    @Autowired
    private TestQuestionService questionService;

    @Autowired
    private Localization loc;

    protected BaseLanguageDefinitionService(Languages language) {
        this.language = language;
    }

    @Override
    public Languages getLanguage() {
        return language;
    }

    // --- texts ---

    @Override
    public ObjectNode getLanguageStringSchema() {
        ObjectNode schema = StructuredOutputs.schemaOf(LanguageString.class).deepCopy();
        customizeLanguageStringSchema(schema);
        return schema;
    }

    /**
     * Changes the generic schema of a text to what the language wants of it. Nothing by default.
     */
    protected void customizeLanguageStringSchema(ObjectNode schema) {
    }

    @Override
    public String getLanguageStringInstructions() {
        return "";
    }

    @Override
    public List<String> checkLanguageString(LanguageString string) {
        return LanguageStrings.checkMetadata(string);
    }

    // --- the second pass over the texts ---

    /**
     * Whether a text the model wrote needs the second pass of the language ({@link #annotatedTextSchema()}). Never by
     * default: the texts stay as they are.
     */
    protected boolean needsAnnotation(String text) {
        return false;
    }

    /** The schema of one text as the second pass writes it. */
    protected ObjectNode annotatedTextSchema() {
        throw new UnsupportedOperationException("The language has no second pass over the texts");
    }

    /** Words of the questions that the second pass must keep together, none by default. */
    protected Set<String> wholeWords(JsonArray questions) {
        return Set.of();
    }

    /**
     * Reads one text of the second pass.
     *
     * @param answer     the text as the model wrote it, in the form of {@link #annotatedTextSchema()}
     * @param expected   the plain text it has to be of
     * @param wholeWords see {@link #wholeWords}
     * @param problems   what is wrong with the answer
     * @return the text with its metadata, null when there are problems
     */
    protected LanguageString readAnnotatedText(JsonElement answer, String expected, Set<String> wholeWords, List<String> problems) {
        throw new UnsupportedOperationException("The language has no second pass over the texts");
    }

    // --- the level gauging test ---

    @Override
    public CancellationToken createGaugeTest(Language language, AI ai, TestCallback callback) throws Exception {
        return createTest(language, ai, loc.getValue(L.LABEL_LEVEL_TEST), TestType.GRADE, gaugeQuestionRequests(), callback);
    }

    @Override
    public CancellationToken createTest(Language language, AI ai, String name, List<TestQuestionRequest> requests,
                                        TestCallback callback) throws Exception {
        return createTest(language, ai, name, TestType.REGULAR, requests, callback);
    }

    private CancellationToken createTest(Language language, AI ai, String name, TestType type,
                                         List<TestQuestionRequest> requests, TestCallback callback) throws Exception {
        if (language == null || ai == null || callback == null || requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("A language, an AI, questions and a callback are required");
        }
        Language profile = languageService.find(language);
        if (profile == null || profile.getLanguage() != this.language) {
            throw new IllegalArgumentException("The profile is not a profile of " + this.language);
        }

        Map<String, TestQuestionFactory> kinds = questionKinds();
        for (int i = 0; i < requests.size(); i++) {
            if (!kinds.containsKey(requests.get(i).getType())) {
                throw new IllegalArgumentException("Unknown kind of question " + requests.get(i).getType());
            }
            requests.get(i).setNumber(i + 1);
        }

        TestGeneration generation = new TestGeneration(profile, ai, name, type, requests, kinds, callback);
        callback.onProgress(0, requests.size());
        generation.sendBatch();
        return generation.token;
    }

    /**
     * The writing of a test, batch after batch. The first request is sent by the thread that starts it, the next ones
     * by the thread the previous answer came on, in the request scope of the user who started it.
     */
    private final class TestGeneration {
        final Language profile;
        final AI ai;
        final String name;
        final TestType type;
        final List<TestQuestionRequest> requests;
        final Map<String, TestQuestionFactory> kinds;
        final TestCallback callback;
        // the answer is handled on a thread of the inference, which does not know who started it
        final ThreadCopyRequestAttributes attributes = ThreadCopyRequestAttributes.create();
        final List<List<TestQuestionRequest>> batches = new ArrayList<>();
        final CancellationToken token = new CancellationToken();
        // the accepted questions at the place of their requests, null for the ones not written yet
        final JsonObject[] slots;
        int done;
        final List<String> reasoning = new ArrayList<>();

        int batch;
        int attempt;
        List<String> problems = List.of();
        // the second pass over the texts of the batch
        int annotationAttempt;
        List<String> annotationProblems = List.of();
        volatile CancellationToken current;

        TestGeneration(Language profile, AI ai, String name, TestType type, List<TestQuestionRequest> requests,
                       Map<String, TestQuestionFactory> kinds, TestCallback callback) {
            this.profile = profile;
            this.ai = ai;
            this.name = name;
            this.type = type;
            this.requests = requests;
            this.kinds = kinds;
            this.callback = callback;
            slots = new JsonObject[requests.size()];
            // a batch is of one kind: a schema that offers several kinds at once is not kept apart by every provider (the
            // model writes the body of one of them for all), and a schema of one kind is easier for any model
            Map<String, List<TestQuestionRequest>> byKind = new LinkedHashMap<>();
            requests.forEach(request -> byKind.computeIfAbsent(request.getType(), kind -> new ArrayList<>()).add(request));
            byKind.values().forEach(sameKind -> {
                for (int i = 0; i < sameKind.size(); i += BATCH_SIZE) {
                    batches.add(sameKind.subList(i, Math.min(i + BATCH_SIZE, sameKind.size())));
                }
            });
            token.onCancel(() -> {
                CancellationToken inner = current;
                if (inner != null) {
                    inner.cancel();
                }
            });
        }

        /** Asks the model for the current batch. */
        void sendBatch() throws Exception {
            List<TestQuestionRequest> asked = batches.get(batch);
            String prompt = templateService.processTemplate(languageService.getCreateTestPrompt(profile),
                    "createTestPrompt", promptVariables(asked, kindsOf(asked), previousQuestions(), problems));
            if (StringUtils.isBlank(prompt)) {
                throw new IllegalStateException("There is no prompt for the tests of " + language);
            }
            JsonNode schema = batchSchema(asked);
            log.debug("Asking for the questions {} of {} (attempt {})", numbers(asked), requests.size(), attempt + 1);

            current = inferenceServices.forAI(ai).infer(List.of(LLMChatMessage.of(LLMRole.USER, prompt)), schema,
                    new BatchCallback(asked));
            if (token.isCancelled()) {
                current.cancel();
            }
        }

        /** The questions written so far, one line each, so that the next ones are about something else. */
        List<String> previousQuestions() {
            List<String> previous = new ArrayList<>();
            for (int i = 0; i < slots.length; i++) {
                if (slots[i] != null) {
                    previous.add(requests.get(i).getNumber() + ". " + summaryOf(slots[i]));
                }
            }
            return previous;
        }

        /** The handlers of the kinds of the questions asked for, each once. */
        List<TestQuestionHandler<?>> kindsOf(List<TestQuestionRequest> asked) {
            Map<String, TestQuestionHandler<?>> used = new LinkedHashMap<>();
            for (TestQuestionRequest request : asked) {
                used.computeIfAbsent(request.getType(), type -> prototype(kinds.get(type)));
            }
            return new ArrayList<>(used.values());
        }

        JsonNode batchSchema(List<TestQuestionRequest> asked) {
            ObjectNode schema = TestDefinitionSchemaGenerator.generate(kindsOf(asked), BaseLanguageDefinitionService.this);
            // exactly the questions asked for
            ((ObjectNode) schema.get("properties").get(TestDefinitionSchemaGenerator.QUESTIONS))
                    .put("minItems", asked.size()).put("maxItems", asked.size());
            return schema;
        }

        /** Runs in the request scope of the user, anything that goes wrong ends the generation. */
        void step(Step step) {
            try (InRequestScope _ = new InRequestScope(attributes)) {
                step.run();
            } catch (Throwable e) {
                fail(e);
            }
        }

        void fail(Throwable e) {
            log.warn("The test of {} could not be made: {}", language, e.getMessage());
            try {
                callback.onError(e);
            } catch (Exception ex) {
                log.error("Error during callback.onError", ex);
            }
        }

        /**
         * The questions of the batch are right; the texts that need more (Japanese: furigana) go to the second pass
         * before the next batch is asked for.
         */
        void checked(List<TestQuestionRequest> asked, JsonArray written, String batchReasoning) throws Exception {
            annotationAttempt = 0;
            annotationProblems = List.of();
            annotate(asked, written, batchReasoning);
        }

        /** The texts of the questions that need the second pass, the same text once; the objects with that text. */
        Map<String, List<JsonObject>> textsToAnnotate(JsonArray written) {
            Map<String, List<JsonObject>> texts = new LinkedHashMap<>();
            for (JsonElement question : written) {
                for (JsonObject text : LanguageStrings.objectsIn(question)) {
                    String raw = text.get(LanguageStrings.RAW_STRING).getAsString();
                    if (needsAnnotation(raw)) {
                        texts.computeIfAbsent(raw, key -> new ArrayList<>()).add(text);
                    }
                }
            }
            return texts;
        }

        void annotate(List<TestQuestionRequest> asked, JsonArray written, String batchReasoning) throws Exception {
            Map<String, List<JsonObject>> texts = textsToAnnotate(written);
            String template = languageService.getAnnotateTextsPrompt(profile);
            if (texts.isEmpty() || StringUtils.isBlank(template)) {
                accepted(asked, written, batchReasoning);
                return;
            }
            List<Map<String, Object>> numbered = new ArrayList<>();
            for (String text : texts.keySet()) {
                numbered.add(Map.of("number", numbered.size() + 1, "text", text));
            }
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("language", StringUtils.capitalize(language.name().toLowerCase()));
            variables.put("textCount", numbered.size());
            variables.put("texts", numbered);
            variables.put("wholeWords", wholeWords(written));
            variables.put("problems", annotationProblems);
            String prompt = templateService.processTemplate(template, "annotateTextsPrompt", variables);

            ObjectNode list = JSON.createObjectNode();
            list.put("type", "array").put("minItems", numbered.size()).put("maxItems", numbered.size());
            list.set("items", annotatedTextSchema());
            ObjectNode schema = JSON.createObjectNode();
            schema.put("type", "object");
            schema.putObject("properties").set(TEXTS, list);
            schema.putArray("required").add(TEXTS);
            schema.put("additionalProperties", false);

            log.debug("Asking for the second pass over {} texts of the questions {} (attempt {})", numbered.size(),
                    numbers(asked), annotationAttempt + 1);
            current = inferenceServices.forAI(ai).infer(List.of(LLMChatMessage.of(LLMRole.USER, prompt)), schema,
                    new AnnotationCallback(asked, written, batchReasoning, texts));
            if (token.isCancelled()) {
                current.cancel();
            }
        }

        void annotationRejected(List<TestQuestionRequest> asked, JsonArray written, String batchReasoning,
                                StructuredOutputException e) throws Exception {
            log.warn("The second pass over the texts of the questions {} of the test of {} was rejected (attempt {} of {}): {}",
                    numbers(asked), language, annotationAttempt + 1, MAX_ATTEMPTS,
                    e.getErrors());
            if (++annotationAttempt >= MAX_ATTEMPTS) {
                throw e;
            }
            annotationProblems = e.getErrors();
            annotate(asked, written, batchReasoning);
        }

        /** The accepted questions in the order of the requests. */
        JsonArray allQuestions() {
            JsonArray all = new JsonArray();
            Arrays.stream(slots).forEach(all::add);
            return all;
        }

        void accepted(List<TestQuestionRequest> asked, JsonArray written, String batchReasoning) throws Exception {
            for (int i = 0; i < asked.size(); i++) {
                slots[asked.get(i).getNumber() - 1] = written.get(i).getAsJsonObject();
            }
            done += asked.size();
            if (StringUtils.isNotBlank(batchReasoning)) {
                reasoning.add("Questions " + numbers(asked) + ":\n" + batchReasoning);
            }
            batch++;
            attempt = 0;
            problems = List.of();
            callback.onProgress(done, requests.size());

            if (batch < batches.size()) {
                sendBatch();
            } else {
                Test test = saveTest(profile, name, type, requests, kinds, allQuestions(), String.join("\n\n", reasoning));
                callback.onTest(test);
            }
        }

        void rejected(List<TestQuestionRequest> asked, StructuredOutputException e) throws Exception {
            log.warn("The questions {} of the test of {} were rejected (attempt {} of {}): {}", numbers(asked), language, attempt + 1, MAX_ATTEMPTS, e.getErrors());
            if (++attempt >= MAX_ATTEMPTS) {
                throw e;
            }
            problems = e.getErrors();
            sendBatch();
        }

        private final class BatchCallback implements InferenceResultCallback {
            private final List<TestQuestionRequest> asked;
            private String batchReasoning;

            BatchCallback(List<TestQuestionRequest> asked) {
                this.asked = asked;
            }

            @Override
            public void onReasoning(String reasoning) {
                this.batchReasoning = reasoning;
            }

            @Override
            public void onResult(String text, SchemaCheck check) {
                if (token.isCancelled()) {
                    onCancelled();
                    return;
                }
                step(() -> {
                    JsonArray written;
                    try {
                        if (!check.valid()) {
                            throw new StructuredOutputException(text, check.errors(), null);
                        }
                        written = readQuestions(text, asked);
                        checkQuestions(text, asked, kinds, written);
                    } catch (StructuredOutputException e) {
                        rejected(asked, e);
                        return;
                    }
                    checked(asked, written, batchReasoning);
                });
            }

            @Override
            public void onCancel() {
                onCancelled();
            }

            private void onCancelled() {
                try {
                    callback.onCancel();
                } catch (Exception e) {
                    log.error("Error during callback.onCancel", e);
                }
            }

            @Override
            public void onError(Throwable exception) {
                fail(exception);
            }
        }

        /** The answer of the second pass: the texts with what the language adds to them. */
        private final class AnnotationCallback implements InferenceResultCallback {
            private final List<TestQuestionRequest> asked;
            private final JsonArray written;
            private final String batchReasoning;
            private final Map<String, List<JsonObject>> texts;

            AnnotationCallback(List<TestQuestionRequest> asked, JsonArray written, String batchReasoning,
                               Map<String, List<JsonObject>> texts) {
                this.asked = asked;
                this.written = written;
                this.batchReasoning = batchReasoning;
                this.texts = texts;
            }

            @Override
            public void onResult(String text, SchemaCheck check) {
                if (token.isCancelled()) {
                    onCancelled();
                    return;
                }
                step(() -> {
                    Map<String, LanguageString> read;
                    try {
                        if (!check.valid()) {
                            throw new StructuredOutputException(text, check.errors(), null);
                        }
                        read = readAnnotations(text);
                    } catch (StructuredOutputException e) {
                        annotationRejected(asked, written, batchReasoning, e);
                        return;
                    }
                    read.forEach((raw, string) -> {
                        JsonElement metadata = new Gson().toJsonTree(string).getAsJsonObject().get(LanguageStrings.METADATA);
                        texts.get(raw).forEach(object -> object.add(LanguageStrings.METADATA, metadata.deepCopy()));
                    });
                    accepted(asked, written, batchReasoning);
                });
            }

            /** The texts of the answer, by the plain text they are of; all of them are fine or it is rejected. */
            private Map<String, LanguageString> readAnnotations(String text) throws StructuredOutputException {
                JsonElement answer;
                try {
                    answer = JsonParser.parseString(StructuredOutputs.cleanJson(text));
                } catch (JsonParseException e) {
                    throw new StructuredOutputException(text, List.of("Not valid JSON: " + e.getMessage()), e);
                }
                JsonArray items = answer.isJsonObject() && answer.getAsJsonObject().has(TEXTS)
                        && answer.getAsJsonObject().get(TEXTS).isJsonArray() ? answer.getAsJsonObject().getAsJsonArray(TEXTS) : null;
                if (items == null || items.size() != texts.size()) {
                    throw new StructuredOutputException(text, List.of("The answer must have " + texts.size() + " texts"), null);
                }
                Set<String> whole = wholeWords(written);
                Map<String, LanguageString> read = new LinkedHashMap<>();
                List<String> problems = new ArrayList<>();
                int number = 0;
                for (String raw : texts.keySet()) {
                    int at = number + 1;
                    List<String> own = new ArrayList<>();
                    LanguageString string = readAnnotatedText(items.get(number++), raw, whole, own);
                    if (string == null || !own.isEmpty()) {
                        if (own.isEmpty()) {
                            own.add("not readable");
                        }
                        own.forEach(problem -> problems.add("Text " + at + " (\"" + raw + "\"): " + problem));
                    } else {
                        read.put(raw, string);
                    }
                }
                if (!problems.isEmpty()) {
                    throw new StructuredOutputException(text, problems, null);
                }
                return read;
            }

            @Override
            public void onCancel() {
                onCancelled();
            }

            private void onCancelled() {
                try {
                    callback.onCancel();
                } catch (Exception e) {
                    log.error("Error during callback.onCancel", e);
                }
            }

            @Override
            public void onError(Throwable exception) {
                fail(exception);
            }
        }
    }

    /** The numbers of the questions of a batch: 1, 3, 5 (they are not next to each other when the kinds are mixed). */
    private static String numbers(List<TestQuestionRequest> asked) {
        return asked.stream().map(request -> String.valueOf(request.getNumber())).collect(java.util.stream.Collectors.joining(", "));
    }

    private interface Step {
        void run() throws Exception;
    }

    /**
     * The questions of the level gauging test in the order they are asked: the reading section, what
     * {@link #questionsBetweenSections} adds, the other section, what {@link #questionsAtEnd} adds. Every question has
     * its level, spread evenly over A1-C2 in each section (what a language adds without levels gets them spread too).
     */
    protected final List<TestQuestionRequest> gaugeQuestionRequests() {
        List<String> names = List.of(SECTION_READING, SECTION_OTHER);
        List<List<TestQuestionRequest>> sections = List.of(readingSection(), otherSection());
        List<TestQuestionRequest> requests = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            requests.addAll(withLevels(sections.get(i)));
            if (i < sections.size() - 1) {
                requests.addAll(withLevels(questionsBetweenSections(names.get(i), names.get(i + 1))));
            }
        }
        requests.addAll(withLevels(questionsAtEnd()));
        for (int i = 0; i < requests.size(); i++) {
            requests.get(i).setNumber(i + 1);
        }
        return requests;
    }

    /** Gives the requests that have no level the levels {@link #spreadLevels} says, in order. */
    private static List<TestQuestionRequest> withLevels(List<TestQuestionRequest> section) {
        List<TestQuestionRequest> without = section.stream().filter(request -> request.getLevel() == null).toList();
        List<CEFR> levels = spreadLevels(without.size());
        for (int i = 0; i < without.size(); i++) {
            without.get(i).setLevel(levels.get(i));
        }
        return section;
    }

    /**
     * {@code count} levels from A1 to C2, as evenly as they go (a count that is a multiple of the 6 levels has every
     * level the same number of times) and easiest first.
     */
    protected static List<CEFR> spreadLevels(int count) {
        CEFR[] levels = CEFR.values();
        List<CEFR> spread = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int index = count == 1 ? (levels.length - 1) / 2
                    : (int) Math.round(i * (levels.length - 1) / (double) (count - 1));
            spread.add(levels[index]);
        }
        return spread;
    }

    /** Questions about a text: single and multiple choice, half and half. */
    protected List<TestQuestionRequest> readingSection() {
        return cycle(SECTION_READING, READING_QUESTIONS, List.of(
                handlerName(new ReadingComprehensionWithSingleChoiceFactory()),
                handlerName(new ReadingComprehensionWithMultipleChoicesFactory())));
    }

    /** The others: single choice (twice as often), multiple choice, writing and word order. */
    protected List<TestQuestionRequest> otherSection() {
        return cycle(SECTION_OTHER, OTHER_QUESTIONS, List.of(
                handlerName(new SingleChoiceFactory()),
                handlerName(new MultipleChoiceFactory()),
                handlerName(new WritingQuestionFactory()),
                handlerName(new WordOrderQuestionFactory()),
                handlerName(new SingleChoiceFactory())));
    }

    /**
     * Questions to ask between two sections of the test.
     *
     * @return none by default
     */
    protected List<TestQuestionRequest> questionsBetweenSections(String previous, String next) {
        return List.of();
    }

    /**
     * Questions to ask after the last section.
     *
     * @return none by default
     */
    protected List<TestQuestionRequest> questionsAtEnd() {
        return List.of();
    }

    /**
     * The kinds of questions the model may be asked for, by the name it writes as the {@code type} of a question
     * ({@code TestQuestionHandler.getLlmTypeName()}). A language that asks for a question of another kind adds it here.
     */
    protected Map<String, TestQuestionFactory> questionKinds() {
        Map<String, TestQuestionFactory> kinds = new LinkedHashMap<>();
        for (TestQuestionFactory factory : List.of(new SingleChoiceFactory(), new MultipleChoiceFactory(),
                new ReadingComprehensionWithSingleChoiceFactory(), new ReadingComprehensionWithMultipleChoicesFactory(),
                new WritingQuestionFactory(), new WordOrderQuestionFactory())) {
            kinds.put(handlerName(factory), factory);
        }
        return kinds;
    }

    /**
     * What the prompt template can use, see {@code LanguageDefaults}.
     *
     * @param asked    the questions of this request
     * @param kinds    the handlers of the kinds of the questions asked for, each once
     * @param previous the questions written already
     * @param problems what was wrong with the last answer to the same request, empty for the first one
     */
    protected Map<String, Object> promptVariables(List<TestQuestionRequest> asked, List<TestQuestionHandler<?>> kinds,
                                                  List<String> previous,
                                                  List<String> problems) throws Exception {
        List<Map<String, String>> levels = new ArrayList<>();
        for (CEFR level : CEFR.values()) {
            levels.add(Map.of("name", level.name(), "description", level.getDescription()));
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("language", StringUtils.capitalize(language.name().toLowerCase()));
        variables.put("levels", levels);
        variables.put("languageStringRules", getLanguageStringInstructions());
        List<Map<String, String>> kindDescriptions = new ArrayList<>();
        for (TestQuestionHandler<?> kind : kinds) {
            kindDescriptions.add(Map.of("name", kind.getLlmTypeName(), "description", kind.getLlmDescription()));
        }
        variables.put("kinds", kindDescriptions);
        variables.put("questionCount", asked.size());
        variables.put("questions", JSON.writerWithDefaultPrettyPrinter().writeValueAsString(asked));
        variables.put("previous", previous);
        variables.put("problems", problems);
        return variables;
    }

    /** One line about a written question: its kind and the texts and words that tell it from the others. */
    private static String summaryOf(JsonObject question) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : question.entrySet()) {
            JsonElement value = entry.getValue();
            if (TestDefinitionSchemaGenerator.TYPE.equals(entry.getKey())) {
                continue;
            }
            if (value.isJsonObject() && value.getAsJsonObject().has(LanguageStrings.RAW_STRING)
                    && value.getAsJsonObject().get(LanguageStrings.RAW_STRING).isJsonPrimitive()) {
                parts.add(StringUtils.abbreviate(value.getAsJsonObject().get(LanguageStrings.RAW_STRING).getAsString(), SUMMARY_LENGTH));
            } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                parts.add(entry.getKey() + " " + StringUtils.abbreviate(value.getAsString(), SUMMARY_LENGTH));
            }
        }
        JsonElement type = question.get(TestDefinitionSchemaGenerator.TYPE);
        return (type == null ? "" : type.getAsString() + ": ") + String.join(" | ", parts);
    }

    // --- from the answer to the entities ---

    /**
     * Makes the entities of the test from the questions, which were all checked already. When saving fails the part
     * that was saved is deleted again.
     */
    private Test saveTest(Language profile, String name, TestType type, List<TestQuestionRequest> requests,
                          Map<String, TestQuestionFactory> kinds, JsonArray questions, String reasoning) throws Exception {
        Test test = new Test();
        test.setLanguage(profile);
        test.setName(name);
        test.setType(type);
        test.setReasoning(reasoning);
        test = testService.save(test);
        try {
            TestQuestionGroup group = new TestQuestionGroup();
            group.setLanguage(profile);
            group.setParentTest(test);
            group.setOrdinal(0);
            group.setFactory(PlainGroupFactory.class.getName());
            group = groupService.save(group);

            for (int i = 0; i < questions.size(); i++) {
                TestQuestionFactory factory = kinds.get(requests.get(i).getType());
                TestQuestion question = new TestQuestion();
                question.setLanguage(profile);
                question.setParentTest(test);
                question.setQuestionGroup(group);
                question.setOrdinal(i);
                question.setTestQuestionFactory(factory.getClass().getName());
                question = questionService.save(question);

                TestQuestionHandler<?> handler = factory.createHandler(test, question);
                handler.setLlmData(dataOf(questions.get(i).getAsJsonObject()));
                handler.save();
            }
            return test;
        } catch (Throwable e) {
            try {
                testService.delete(test, true);
            } catch (Exception cleanup) {
                log.error("The unfinished test could not be deleted", cleanup);
            }
            throw e;
        }
    }

    /** The questions array of the answer, with as many questions as were asked. */
    private JsonArray readQuestions(String text, List<TestQuestionRequest> asked) throws StructuredOutputException {
        JsonElement answer;
        try {
            answer = JsonParser.parseString(StructuredOutputs.cleanJson(text));
        } catch (JsonParseException e) {
            throw new StructuredOutputException(text, List.of("Not valid JSON: " + e.getMessage()), e);
        }
        if (!answer.isJsonObject() || !answer.getAsJsonObject().has(TestDefinitionSchemaGenerator.QUESTIONS)
                || !answer.getAsJsonObject().get(TestDefinitionSchemaGenerator.QUESTIONS).isJsonArray()) {
            throw new StructuredOutputException(text, List.of("The answer has no list of questions"), null);
        }
        JsonArray questions = answer.getAsJsonObject().getAsJsonArray(TestDefinitionSchemaGenerator.QUESTIONS);
        if (questions.size() != asked.size()) {
            throw new StructuredOutputException(text, List.of("The answer has " + questions.size()
                    + " questions, " + asked.size() + " were asked for"), null);
        }
        return questions;
    }

    /** Every question is of the kind that was asked for, can be read and its texts are what the language wants. */
    private void checkQuestions(String text, List<TestQuestionRequest> asked, Map<String, TestQuestionFactory> kinds,
                                JsonArray questions) throws StructuredOutputException {
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            String where = "Question " + asked.get(i).getNumber() + ": ";
            if (!questions.get(i).isJsonObject()) {
                problems.add(where + "not an object");
                continue;
            }
            JsonObject question = questions.get(i).getAsJsonObject();
            String expected = asked.get(i).getType();
            String written = question.has(TestDefinitionSchemaGenerator.TYPE) && question.get(TestDefinitionSchemaGenerator.TYPE).isJsonPrimitive()
                    ? question.get(TestDefinitionSchemaGenerator.TYPE).getAsString() : null;
            if (!expected.equals(written)) {
                problems.add(where + "should be " + expected + ", is " + written);
                continue;
            }
            try {
                TestQuestionHandler<?> handler = prototype(kinds.get(expected));
                // in place: what is saved and what the second pass reads is the question in the form of the data
                handler.normalizeLlmData(question);
                handler.setLlmData(dataOf(question));
            } catch (IllegalArgumentException e) {
                problems.add(where + e.getMessage());
                continue;
            }
            Set<String> languageProblems = new LinkedHashSet<>();
            for (LanguageString string : LanguageStrings.findIn(question)) {
                languageProblems.addAll(checkLanguageString(string));
            }
            languageProblems.forEach(problem -> problems.add(where + problem));
        }
        if (!problems.isEmpty()) {
            throw new StructuredOutputException(text, problems, null);
        }
    }

    /** The question without the {@code type}, which is not part of the data. */
    private static JsonObject dataOf(JsonObject question) {
        JsonObject data = question.deepCopy();
        data.remove(TestDefinitionSchemaGenerator.TYPE);
        return data;
    }

    /** A handler of a question that is not saved anywhere: for its name, its schema and for checking data. */
    private static TestQuestionHandler<?> prototype(TestQuestionFactory factory) {
        return factory.createHandler(new Test(), new TestQuestion());
    }

    /** The name the model writes as the {@code type} of a question of the factory. */
    protected static String handlerName(TestQuestionFactory factory) {
        return prototype(factory).getLlmTypeName();
    }

    /** The requests of a section; their levels are filled in by {@link #withLevels}. */
    private static List<TestQuestionRequest> cycle(String section, int count, List<String> types) {
        List<TestQuestionRequest> requests = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            requests.add(new TestQuestionRequest(section, types.get(i % types.size()), null));
        }
        return requests;
    }

}
