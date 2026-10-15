package com.github.enerccio.lingollm.ui;

import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.collections.TestType;
import com.github.enerccio.lingollm.domain.factories.TestQuestionFactory;
import com.github.enerccio.lingollm.domain.factories.impl.KanjiReadingFactory;
import com.github.enerccio.lingollm.domain.factories.impl.MultipleChoiceFactory;
import com.github.enerccio.lingollm.domain.factories.impl.PlainGroupFactory;
import com.github.enerccio.lingollm.domain.factories.impl.ReadingComprehensionWithSingleChoiceFactory;
import com.github.enerccio.lingollm.domain.factories.impl.SingleChoiceFactory;
import com.github.enerccio.lingollm.domain.factories.impl.WordOrderQuestionFactory;
import com.github.enerccio.lingollm.domain.factories.impl.WritingQuestionFactory;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionGroup;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionResult;
import com.github.enerccio.lingollm.domain.model.impl.TestResult;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.github.enerccio.lingollm.domain.service.TestQuestionGroupService;
import com.github.enerccio.lingollm.domain.service.TestQuestionResultService;
import com.github.enerccio.lingollm.domain.service.TestQuestionService;
import com.github.enerccio.lingollm.domain.service.TestResultService;
import com.github.enerccio.lingollm.domain.service.TestService;
import com.github.enerccio.lingollm.test.LingoLLMTestBase;
import com.github.enerccio.lingollm.ui.dialogs.TestDialog;
import com.github.enerccio.lingollm.ui.dialogs.TestGenerationDialog;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionHandler;
import com.github.enerccio.lingollm.ui.lang.languages.common.impl.PlainGroupHandler;
import com.github.enerccio.lingollm.ui.lang.languages.common.impl.ReadingTextGroupHandler;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.japanese.KanjiReadingHandler;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code TestQuestionHandler.render()} and {@code getAnswer()} of the kinds of questions, and the {@link TestDialog}
 * that shows them.
 */
class TestRenderingTest extends LingoLLMTestBase {

    private static final String SENTENCE = """
            {"rawString": "学校へ行く", "metadata": [
              {"type": "furigana", "start": 0, "end": 2, "value": "がっこう"},
              {"type": "furigana", "start": 3, "end": 4, "value": "い"}]}""";

    @Autowired
    private LanguageService languageService;

    @Autowired
    private TestService testService;

    @Autowired
    private TestQuestionService questionService;

    @Autowired
    private TestQuestionGroupService groupService;

    @Autowired
    private TestResultService resultService;

    @Autowired
    private TestQuestionResultService answerService;

    private Test test;

    @BeforeEach
    void testWithProfile() throws Exception {
        login();
        Language language = new Language();
        language.setLanguage(Languages.JAPANESE);
        Test t = new Test();
        t.setName("Level test");
        t.setLanguage(languageService.save(language));
        test = testService.save(t);
    }

    private static Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(TestRenderingTest::descendants));
    }

    private static <T extends Component> T find(Component root, Class<T> type) {
        return descendants(root).filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    private static <T extends Component> T find(TestDialog dialog, Class<T> type) {
        return inDialog(dialog).filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    private static void click(Button button) {
        ComponentUtil.fireEvent(button, new ClickEvent<>(button));
    }

    private TestQuestionHandler<?> handler(TestQuestionFactory factory, String json) {
        TestQuestion question = new TestQuestion();
        question.setLanguage(test.getLanguage());
        TestQuestionHandler<?> handler = factory.createHandler(test, question);
        handler.setLlmData(JsonParser.parseString(json).getAsJsonObject());
        return handler;
    }

    private static final String CHOICES = """
            "instructions": {"rawString": "Pick"}, "choices": [{"rawString": "a"}, {"rawString": "b"}, {"rawString": "c"}]""";

    @org.junit.jupiter.api.Test
    void singleChoiceAnswersWithTheIndexOfTheChoice() {
        TestQuestionHandler<?> handler = handler(new SingleChoiceFactory(), "{" + CHOICES + ", \"correctAnswer\": 1}");

        Component view = handler.render();
        assertThat(handler.getAnswer()).isNull();
        RadioButtonGroup<Integer> group = find(view, RadioButtonGroup.class);
        assertThat(group.getListDataView().getItems()).containsExactly(0, 1, 2);
        group.setValue(2);

        assertThat(handler.getAnswer()).isEqualTo("2");
    }

    @org.junit.jupiter.api.Test
    void readingQuestionShowsTheText() {
        TestQuestionHandler<?> handler = handler(new ReadingComprehensionWithSingleChoiceFactory(),
                "{" + CHOICES + ", \"correctAnswer\": 0, \"text\": {\"rawString\": \"Ein Text.\"}}");

        Component view = handler.render();

        assertThat(view.getElement().getTextRecursively()).contains("Ein Text.").contains("Pick");
    }

    @org.junit.jupiter.api.Test
    void multipleChoiceAnswersWithTheSortedIndexes() {
        TestQuestionHandler<?> handler = handler(new MultipleChoiceFactory(), "{" + CHOICES + ", \"correctAnswers\": [0]}");

        Component view = handler.render();
        // choosing nothing is an answer too
        assertThat(handler.getAnswer()).isEqualTo("[]");
        CheckboxGroup<Integer> group = find(view, CheckboxGroup.class);
        group.setValue(Set.of(2, 0));

        assertThat(handler.getAnswer()).isEqualTo("[0,2]");
    }

    @org.junit.jupiter.api.Test
    void writingQuestionAnswersWithTheTrimmedText() {
        TestQuestionHandler<?> handler = handler(new WritingQuestionFactory(),
                "{\"instructions\": {\"rawString\": \"Write\"}, \"sampleAnswer\": {\"rawString\": \"Hallo\"}}");

        Component view = handler.render();
        assertThat(handler.getAnswer()).isNull();
        find(view, TextArea.class).setValue("  Hallo Welt \n");

        assertThat(handler.getAnswer()).isEqualTo("Hallo Welt");
    }

    @org.junit.jupiter.api.Test
    void wordOrderIsAnsweredWhenAllSegmentsArePlaced() {
        TestQuestionHandler<?> handler = handler(new WordOrderQuestionFactory(),
                "{\"instructions\": {\"rawString\": \"Order\"}, \"segments\": [{\"rawString\": \"Ich\"}, {\"rawString\": \"heisse\"}, {\"rawString\": \"Anna\"}]}");

        Component view = handler.render();
        List<Div> areas = descendants(view).filter(Div.class::isInstance).map(Div.class::cast)
                .filter(div -> div.hasClassName("word-order-area")).toList();
        Div answerArea = areas.stream().filter(div -> div.hasClassName("word-order-answer")).findFirst().orElseThrow();
        Div pool = areas.stream().filter(div -> !div.hasClassName("word-order-answer")).findFirst().orElseThrow();
        assertThat(pool.getChildren().count()).isEqualTo(3);

        // two of three is not an answer
        click((Button) pool.getChildren().findFirst().orElseThrow());
        click((Button) pool.getChildren().findFirst().orElseThrow());
        assertThat(handler.getAnswer()).isNull();
        assertThat(answerArea.getChildren().count()).isEqualTo(2);

        // a segment of the answer goes back to the pool
        click((Button) answerArea.getChildren().findFirst().orElseThrow());
        assertThat(answerArea.getChildren().count()).isEqualTo(1);
        assertThat(pool.getChildren().count()).isEqualTo(2);

        while (pool.getChildren().count() > 0) {
            click((Button) pool.getChildren().findFirst().orElseThrow());
        }
        JsonObject answer = new JsonObject();
        answer.add("order", JsonParser.parseString(handler.getAnswer()));
        assertThat(answer.getAsJsonArray("order")).hasSize(3);
        assertThat(answer.getAsJsonArray("order").asList().stream().map(e -> e.getAsInt())).containsExactlyInAnyOrder(0, 1, 2);
    }

    @org.junit.jupiter.api.Test
    void kanjiReadingHidesTheFuriganaOfTheTarget() {
        String data = """
                {"instructions": {"rawString": "Pick the reading"}, "sentence": %s, "targetKanji": "学校",
                 "choices": [{"rawString": "がっこう"}, {"rawString": "がくこう"}], "correctAnswer": 0}""".formatted(SENTENCE);
        TestQuestionHandler<?> handler = handler(new KanjiReadingFactory(), data);

        Component view = handler.render();

        // only the reading of 行 is drawn in the sentence
        List<String> readings = descendants(view).flatMap(c -> LanguageStringComponentTest.elements(c.getElement(), "rt").stream())
                .map(e -> e.getText()).distinct().toList();
        assertThat(readings).containsExactly("い");
        // the learner is told which kanji is asked
        assertThat(view.getElement().getTextRecursively()).contains("学校").contains("Reading of");
        assertThat(handler.getAnswer()).isNull();
        find(view, RadioButtonGroup.class).setValue(1);
        assertThat(handler.getAnswer()).isEqualTo("1");
    }

    @org.junit.jupiter.api.Test
    void kanjiReadingChecksItsData() {
        TestQuestion question = new TestQuestion();
        KanjiReadingHandler handler = (KanjiReadingHandler) new KanjiReadingFactory().createHandler(test, question);

        String missing = """
                {"sentence": %s, "targetKanji": "犬", "choices": [{"rawString": "a"}, {"rawString": "b"}], "correctAnswer": 0}"""
                .formatted(SENTENCE);
        assertThatThrownBy(() -> handler.setLlmData(JsonParser.parseString(missing).getAsJsonObject()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not in the sentence");

        String noTarget = """
                {"sentence": %s, "choices": [{"rawString": "a"}, {"rawString": "b"}], "correctAnswer": 0}""".formatted(SENTENCE);
        assertThatThrownBy(() -> handler.setLlmData(JsonParser.parseString(noTarget).getAsJsonObject()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("target");
    }

    @org.junit.jupiter.api.Test
    void aGroupShowsItsInstructionsAndText() {
        TestQuestionGroup group = new TestQuestionGroup();
        PlainGroupHandler plain = new PlainGroupHandler(test, group);
        assertThat(plain.render()).isNull();
        plain.setInstructions(LanguageString.of("Read and answer"));
        assertThat(plain.render().getElement().getTextRecursively()).isEqualTo("Read and answer");

        ReadingTextGroupHandler reading = new ReadingTextGroupHandler(test, new TestQuestionGroup());
        reading.setText(LanguageString.of("Ein Text."));
        assertThat(reading.render().getElement().getTextRecursively()).isEqualTo("Ein Text.");
    }

    // --- the dialog ---

    private TestQuestion save(TestQuestionGroup group, int ordinal, TestQuestionFactory factory, String json) throws Exception {
        TestQuestion question = new TestQuestion();
        question.setLanguage(test.getLanguage());
        question.setParentTest(test);
        question.setQuestionGroup(group);
        question.setOrdinal(ordinal);
        question.setTestQuestionFactory(factory.getClass().getName());
        question = questionService.save(question);
        TestQuestionHandler<?> handler = factory.createHandler(test, question);
        handler.setLlmData(JsonParser.parseString(json).getAsJsonObject());
        handler.save();
        return question;
    }

    private TestQuestionGroup saveGroup() throws Exception {
        TestQuestionGroup group = new TestQuestionGroup();
        group.setLanguage(test.getLanguage());
        group.setParentTest(test);
        group.setOrdinal(0);
        group.setFactory(PlainGroupFactory.class.getName());
        return groupService.save(group);
    }

    /** Everything in the dialog, the footer too. */
    private static Stream<Component> inDialog(TestDialog dialog) {
        Stream<Component> footer = dialog.getFooter().getElement().getChildren()
                .flatMap(element -> element.getComponent().stream());
        return Stream.concat(descendants(dialog), footer.flatMap(TestRenderingTest::descendants));
    }

    private static Button buttonOf(TestDialog dialog, String text) {
        return inDialog(dialog).filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
    }

    @org.junit.jupiter.api.Test
    void theDialogWalksThroughTheQuestionsAndCollectsTheAnswers() throws Exception {
        TestQuestionGroup group = saveGroup();
        TestQuestion first = save(group, 0, new SingleChoiceFactory(), "{" + CHOICES + ", \"correctAnswer\": 1}");
        TestQuestion second = save(group, 1, new WritingQuestionFactory(),
                "{\"instructions\": {\"rawString\": \"Write\"}, \"sampleAnswer\": {\"rawString\": \"Hallo\"}}");

        TestDialog dialog = new TestDialog(testService.find(test));
        AtomicReference<List<TestDialog.Answer>> finished = new AtomicReference<>();
        dialog.setOnFinish(finished::set);
        dialog.create();

        // the first question is shown
        assertThat(inDialog(dialog).anyMatch(RadioButtonGroup.class::isInstance)).isTrue();
        assertThat(buttonOf(dialog, "Previous").isEnabled()).isFalse();
        find(dialog, RadioButtonGroup.class).setValue(1);
        click(buttonOf(dialog, "Next"));

        // the second one, the last: don't know instead of an answer
        assertThat(inDialog(dialog).anyMatch(TextArea.class::isInstance)).isTrue();
        assertThat(buttonOf(dialog, "Previous").isEnabled()).isTrue();
        Checkbox dontKnow = inDialog(dialog).filter(Checkbox.class::isInstance).map(Checkbox.class::cast)
                .filter(box -> "I don't know".equals(box.getLabel())).findFirst().orElseThrow();
        dontKnow.setValue(true);
        assertThat(find(dialog, TextArea.class).getElement().isEnabled()).isFalse();
        click(buttonOf(dialog, "Finish"));

        assertThat(finished.get()).hasSize(2);
        assertThat(finished.get().get(0).question().getId()).isEqualTo(first.getId());
        assertThat(finished.get().get(0).answer()).isEqualTo("1");
        assertThat(finished.get().get(0).dontKnow()).isFalse();
        assertThat(finished.get().get(1).question().getId()).isEqualTo(second.getId());
        assertThat(finished.get().get(1).answer()).isNull();
        assertThat(finished.get().get(1).dontKnow()).isTrue();
    }

    @org.junit.jupiter.api.Test
    void aRecordedAnswerFillsTheInputsAgain() {
        TestQuestionHandler<?> single = handler(new SingleChoiceFactory(), "{" + CHOICES + ", \"correctAnswer\": 1}");
        RadioButtonGroup<Integer> radios = find(single.render(), RadioButtonGroup.class);
        single.setAnswer("2");
        assertThat(radios.getValue()).isEqualTo(2);
        assertThat(single.getAnswer()).isEqualTo("2");
        // what does not fit is ignored
        single.setAnswer("7");
        single.setAnswer("x");
        single.setAnswer(null);
        assertThat(radios.getValue()).isEqualTo(2);

        TestQuestionHandler<?> multiple = handler(new MultipleChoiceFactory(), "{" + CHOICES + ", \"correctAnswers\": [0, 2]}");
        CheckboxGroup<Integer> boxes = find(multiple.render(), CheckboxGroup.class);
        multiple.setAnswer("[2, 0, 9]");
        assertThat(boxes.getValue()).containsExactlyInAnyOrder(0, 2);
        multiple.setAnswer("nonsense");
        assertThat(boxes.getValue()).containsExactlyInAnyOrder(0, 2);
        multiple.setAnswer("[]");
        assertThat(multiple.getAnswer()).isEqualTo("[]");

        TestQuestionHandler<?> writing = handler(new WritingQuestionFactory(),
                "{\"instructions\": {\"rawString\": \"Write\"}, \"sampleAnswer\": {\"rawString\": \"Hallo\"}}");
        writing.render();
        writing.setAnswer("Ich heisse Anna.");
        assertThat(writing.getAnswer()).isEqualTo("Ich heisse Anna.");

        TestQuestionHandler<?> wordOrder = handler(new WordOrderQuestionFactory(),
                "{\"instructions\": {\"rawString\": \"Order\"}, \"segments\": [{\"rawString\": \"Ich\"}, {\"rawString\": \"heisse\"}, {\"rawString\": \"Anna\"}]}");
        Component view = wordOrder.render();
        wordOrder.setAnswer("[2, 0, 1, 1, 5]");
        // the segments that are not there or twice are left out
        assertThat(wordOrder.getAnswer()).isEqualTo("[2,0,1]");
        wordOrder.setAnswer("[1, 9]");
        assertThat(wordOrder.getAnswer()).isNull();
        wordOrder.setAnswer("[2, 0, 1]");
        Div answerArea = descendants(view).filter(Div.class::isInstance).map(Div.class::cast)
                .filter(div -> div.hasClassName("word-order-answer")).findFirst().orElseThrow();
        assertThat(answerArea.getChildren().count()).isEqualTo(3);
    }

    @org.junit.jupiter.api.Test
    void theDialogRecordsEveryAnswerAndContinuesWhereTheLearnerStopped() throws Exception {
        TestQuestionGroup group = saveGroup();
        TestQuestion first = save(group, 0, new SingleChoiceFactory(), "{" + CHOICES + ", \"correctAnswer\": 1}");
        TestQuestion second = save(group, 1, new WritingQuestionFactory(),
                "{\"instructions\": {\"rawString\": \"Write\"}, \"sampleAnswer\": {\"rawString\": \"Hallo\"}}");
        TestQuestion third = save(group, 2, new SingleChoiceFactory(), "{" + CHOICES + ", \"correctAnswer\": 0}");
        TestResult result = resultService.start(testService.find(test));

        TestDialog dialog = new TestDialog(testService.find(test), result);
        dialog.create();
        // nothing is recorded before a question is left
        assertThat(answerService.findByTestResult(result)).isEmpty();
        find(dialog, RadioButtonGroup.class).setValue(1);
        click(buttonOf(dialog, "Next"));
        assertThat(answerService.findByTestResult(result)).extracting(TestQuestionResult::getAnswer).containsExactly("1");

        // back and another answer: rewritten, not added
        click(buttonOf(dialog, "Previous"));
        find(dialog, RadioButtonGroup.class).setValue(0);
        click(buttonOf(dialog, "Next"));
        // (the second question was left too, without an answer)
        assertThat(answerService.findByTestResult(result)).extracting(TestQuestionResult::getAnswer).containsExactly("0", null);

        find(dialog, TextArea.class).setValue("Ich heisse Anna.");
        click(buttonOf(dialog, "Next"));
        assertThat(answerService.findByTestResult(result)).extracting(TestQuestionResult::getAnswer)
                .containsExactly("0", "Ich heisse Anna.");
        // the learner leaves the test in the third question, which has no answer
        dialog.close();
        assertThat(resultService.findUnfinished(test.getLanguage(), TestType.REGULAR).getId()).isEqualTo(result.getId());

        // the test is opened again: it starts at the question without an answer, the others show theirs
        TestDialog again = new TestDialog(testService.find(test), resultService.findByTest(test).getFirst());
        AtomicReference<List<TestDialog.Answer>> finished = new AtomicReference<>();
        again.setOnFinish(finished::set);
        again.create();
        assertThat(find(again, RadioButtonGroup.class).getValue()).isNull();
        assertThat(buttonOf(again, "Finish")).isNotNull();
        find(again, RadioButtonGroup.class).setValue(2);
        click(buttonOf(again, "Previous"));
        assertThat(find(again, TextArea.class).getValue()).isEqualTo("Ich heisse Anna.");
        click(buttonOf(again, "Previous"));
        assertThat(find(again, RadioButtonGroup.class).getValue()).isEqualTo(0);
        click(buttonOf(again, "Next"));
        click(buttonOf(again, "Next"));
        click(buttonOf(again, "Finish"));

        TestResult done = resultService.findByTest(test).getFirst();
        assertThat(done.isFinished()).isTrue();
        assertThat(resultService.findUnfinished(test.getLanguage(), TestType.REGULAR)).isNull();
        assertThat(answerService.findByTestResult(done)).extracting(TestQuestionResult::getAnswer)
                .containsExactly("0", "Ich heisse Anna.", "2");
        assertThat(answerService.findByTestResult(done)).extracting(r -> r.getQuestion().getId())
                .containsExactly(first.getId(), second.getId(), third.getId());
        assertThat(finished.get()).hasSize(3);
    }

    @org.junit.jupiter.api.Test
    void theDialogTellsWhenItIsClosed() throws Exception {
        TestQuestionGroup group = saveGroup();
        save(group, 0, new SingleChoiceFactory(), "{" + CHOICES + ", \"correctAnswer\": 1}");
        TestDialog dialog = new TestDialog(testService.find(test), resultService.start(testService.find(test)));
        java.util.concurrent.atomic.AtomicInteger closed = new java.util.concurrent.atomic.AtomicInteger();
        dialog.setOnClose(closed::incrementAndGet);
        dialog.create();

        // (the dialog is not opened in a test, the event of the closing is fired by hand)
        ComponentUtil.fireEvent(dialog, new com.vaadin.flow.component.dialog.Dialog.OpenedChangeEvent(dialog, false));

        assertThat(closed.get()).isEqualTo(1);
    }

    @org.junit.jupiter.api.Test
    void theDialogShowsWhatTheGroupSharesAboveTheQuestion() throws Exception {
        TestQuestionGroup group = saveGroup();
        PlainGroupHandler handler = new PlainGroupHandler(test, group);
        handler.setInstructions(LanguageString.of("Shared instructions"));
        handler.save();
        save(group, 0, new SingleChoiceFactory(), "{" + CHOICES + ", \"correctAnswer\": 1}");

        TestDialog dialog = new TestDialog(testService.find(test));
        dialog.create();

        assertThat(inDialog(dialog).anyMatch(c -> c.getElement().getTextRecursively().contains("Shared instructions"))).isTrue();
    }

    @org.junit.jupiter.api.Test
    void theGenerationDialogShowsHowManyQuestionsAreWrittenAndStops() {
        AtomicReference<Boolean> stopped = new AtomicReference<>(false);
        TestGenerationDialog dialog = new TestGenerationDialog(() -> stopped.set(true));
        dialog.create();

        dialog.setProgress(9, 21);

        ProgressBar bar = descendants(dialog).filter(ProgressBar.class::isInstance).map(ProgressBar.class::cast)
                .findFirst().orElseThrow();
        assertThat(bar.getValue()).isEqualTo(9);
        assertThat(bar.getMax()).isEqualTo(21);
        assertThat(bar.isIndeterminate()).isFalse();
        assertThat(descendants(dialog).anyMatch(c -> c.getElement().getText().equals("9 of 21 questions written, 12 left"))).isTrue();
        Button stop = dialog.getFooter().getElement().getChildren().flatMap(e -> e.getComponent().stream())
                .filter(Button.class::isInstance).map(Button.class::cast).findFirst().orElseThrow();
        assertThat(stop.getText()).isEqualTo("Stop Generation");
        click(stop);
        assertThat(stopped.get()).isTrue();
    }

    @org.junit.jupiter.api.Test
    void theDialogOfATestWithoutQuestionsOpensAndCannotMove() throws Exception {
        TestDialog dialog = new TestDialog(testService.find(test));
        dialog.create();

        assertThat(buttonOf(dialog, "Next").isEnabled()).isFalse();
    }
}
