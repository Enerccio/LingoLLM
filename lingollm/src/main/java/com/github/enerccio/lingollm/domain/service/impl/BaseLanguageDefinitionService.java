package com.github.enerccio.lingollm.domain.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.enerccio.lingollm.domain.collections.CEFR;
import com.github.enerccio.lingollm.domain.collections.Languages;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base of the definitions of the languages: keeps the language and holds what all of them share, above all the
 * level gauging test.
 * <p>
 * The test has {@value #READING_QUESTIONS} questions about a text ({@link #SECTION_READING}) and
 * {@value #OTHER_QUESTIONS} others ({@link #SECTION_OTHER}), all in one group. A language can add questions between
 * the sections ({@link #questionsBetweenSections}) and at the end ({@link #questionsAtEnd}), and a different kind of
 * question needs only {@link #questionKinds}.
 */
public abstract class BaseLanguageDefinitionService implements LanguageDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(BaseLanguageDefinitionService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    public static final String SECTION_READING = "reading";
    public static final String SECTION_OTHER = "other";
    public static final int READING_QUESTIONS = 5;
    public static final int OTHER_QUESTIONS = 15;

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

    // --- the level gauging test ---

    @Override
    public CancellationToken createGaugeTest(Language language, AI ai, GaugeTestCallback callback) throws Exception {
        if (language == null || ai == null || callback == null) {
            throw new IllegalArgumentException("A language, an AI and a callback are required");
        }
        Language profile = languageService.find(language);
        if (profile == null || profile.getLanguage() != this.language) {
            throw new IllegalArgumentException("The profile is not a profile of " + this.language);
        }

        List<TestQuestionRequest> requests = gaugeQuestionRequests();
        Map<String, TestQuestionFactory> kinds = questionKinds();
        Map<String, TestQuestionHandler<?>> used = new LinkedHashMap<>();
        for (TestQuestionRequest request : requests) {
            TestQuestionFactory factory = kinds.get(request.getType());
            if (factory == null) {
                throw new IllegalArgumentException("Unknown kind of question " + request.getType());
            }
            used.computeIfAbsent(request.getType(), type -> prototype(factory));
        }

        String prompt = templateService.processTemplate(languageService.getGaugeTestPrompt(profile),
                "gaugeTestPrompt", gaugeVariables(profile, requests));
        if (StringUtils.isBlank(prompt)) {
            throw new IllegalStateException("There is no prompt for the level test of " + this.language);
        }
        JsonNode schema = TestDefinitionSchemaGenerator.generate(new ArrayList<>(used.values()), this);
        String name = loc.getValue(L.LABEL_LEVEL_TEST);
        // the answer is handled on a thread of the inference, which does not know who started it
        ThreadCopyRequestAttributes attributes = ThreadCopyRequestAttributes.create();

        return inferenceServices.forAI(ai).infer(List.of(LLMChatMessage.of(LLMRole.USER, prompt)), schema,
                new InferenceResultCallback() {
                    private String reasoning;

                    @Override
                    public void onReasoning(String reasoning) {
                        this.reasoning = reasoning;
                    }

                    @Override
                    public void onResult(String text, SchemaCheck check) throws Exception {
                        Test test;
                        try (InRequestScope _ = new InRequestScope(attributes)) {
                            if (!check.valid()) {
                                throw new StructuredOutputException(text, check.errors(), null);
                            }
                            test = saveGaugeTest(profile, name, requests, kinds, text, reasoning);
                        } catch (Throwable e) {
                            log.warn("The level test of {} could not be made: {}", BaseLanguageDefinitionService.this.language, e.getMessage());
                            callback.onError(e);
                            return;
                        }
                        callback.onTest(test);
                    }

                    @Override
                    public void onCancel() throws Exception {
                        callback.onCancel();
                    }

                    @Override
                    public void onError(Throwable exception) throws Exception {
                        callback.onError(exception);
                    }
                });
    }

    /**
     * The questions of the test in the order they are asked: the reading section, what
     * {@link #questionsBetweenSections} adds, the other section, what {@link #questionsAtEnd} adds.
     */
    protected final List<TestQuestionRequest> gaugeQuestionRequests() {
        List<String> names = List.of(SECTION_READING, SECTION_OTHER);
        List<List<TestQuestionRequest>> sections = List.of(readingSection(), otherSection());
        List<TestQuestionRequest> requests = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            requests.addAll(sections.get(i));
            if (i < sections.size() - 1) {
                requests.addAll(questionsBetweenSections(names.get(i), names.get(i + 1)));
            }
        }
        requests.addAll(questionsAtEnd());
        for (int i = 0; i < requests.size(); i++) {
            requests.get(i).setNumber(i + 1);
        }
        return requests;
    }

    /** Questions about a text: single and multiple choice, 3 and 2. */
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
     */
    protected Map<String, Object> gaugeVariables(Language profile, List<TestQuestionRequest> requests) throws Exception {
        List<Map<String, String>> levels = new ArrayList<>();
        for (CEFR level : CEFR.values()) {
            levels.add(Map.of("name", level.name(), "description", level.getDescription()));
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("language", StringUtils.capitalize(language.name().toLowerCase()));
        variables.put("userGrade", profile.getUserGrade() == null ? "unknown" : profile.getUserGrade().name());
        variables.put("userGradeFineDetails", StringUtils.defaultString(profile.getUserGradeFineDetails()));
        variables.put("levels", levels);
        variables.put("languageStringRules", getLanguageStringInstructions());
        variables.put("questionCount", requests.size());
        variables.put("questions", JSON.writerWithDefaultPrettyPrinter().writeValueAsString(requests));
        return variables;
    }

    // --- from the answer to the entities ---

    /**
     * Makes the entities of the test from the answer. Everything is checked before anything is saved; when saving
     * fails the part that was saved is deleted again.
     */
    private Test saveGaugeTest(Language profile, String name, List<TestQuestionRequest> requests,
                               Map<String, TestQuestionFactory> kinds, String text, String reasoning) throws Exception {
        JsonArray questions = readQuestions(text, requests);
        checkQuestions(text, requests, kinds, questions);

        Test test = new Test();
        test.setLanguage(profile);
        test.setName(name);
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
                log.error("The unfinished level test could not be deleted", cleanup);
            }
            throw e;
        }
    }

    /** The questions array of the answer, with as many questions as were asked. */
    private JsonArray readQuestions(String text, List<TestQuestionRequest> requests) throws StructuredOutputException {
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
        if (questions.size() != requests.size()) {
            throw new StructuredOutputException(text, List.of("The test has " + questions.size()
                    + " questions, " + requests.size() + " were asked for"), null);
        }
        return questions;
    }

    /** Every question is of the kind that was asked for, can be read and its texts are what the language wants. */
    private void checkQuestions(String text, List<TestQuestionRequest> requests, Map<String, TestQuestionFactory> kinds,
                                JsonArray questions) throws StructuredOutputException {
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            String where = "Question " + (i + 1) + ": ";
            if (!questions.get(i).isJsonObject()) {
                problems.add(where + "not an object");
                continue;
            }
            JsonObject question = questions.get(i).getAsJsonObject();
            String asked = requests.get(i).getType();
            String written = question.has(TestDefinitionSchemaGenerator.TYPE) && question.get(TestDefinitionSchemaGenerator.TYPE).isJsonPrimitive()
                    ? question.get(TestDefinitionSchemaGenerator.TYPE).getAsString() : null;
            if (!asked.equals(written)) {
                problems.add(where + "should be " + asked + ", is " + written);
                continue;
            }
            try {
                prototype(kinds.get(asked)).setLlmData(dataOf(question));
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

    private static String handlerName(TestQuestionFactory factory) {
        return prototype(factory).getLlmTypeName();
    }

    private static List<TestQuestionRequest> cycle(String section, int count, List<String> types) {
        List<TestQuestionRequest> requests = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            requests.add(new TestQuestionRequest(section, types.get(i % types.size()), null));
        }
        return requests;
    }

}
