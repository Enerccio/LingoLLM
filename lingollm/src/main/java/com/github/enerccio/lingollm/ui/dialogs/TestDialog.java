package com.github.enerccio.lingollm.ui.dialogs;

import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.domain.factories.TestQuestionFactory;
import com.github.enerccio.lingollm.domain.factories.TestQuestionGroupFactory;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionGroup;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionResult;
import com.github.enerccio.lingollm.domain.model.impl.TestResult;
import com.github.enerccio.lingollm.domain.service.TestQuestionFactoryRegistry;
import com.github.enerccio.lingollm.domain.service.TestQuestionGroupFactoryRegistry;
import com.github.enerccio.lingollm.domain.service.TestQuestionGroupService;
import com.github.enerccio.lingollm.domain.service.TestQuestionResultService;
import com.github.enerccio.lingollm.domain.service.TestQuestionService;
import com.github.enerccio.lingollm.domain.service.TestResultService;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionGroupHandler;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionHandler;
import com.github.enerccio.lingollm.utils.UIUtils;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.page.ExtendedClientDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Takes a {@link Test}: the questions one after another, each one the component its
 * {@link TestQuestionHandler#render() handler} renders, with the shared content of its group above it. The learner can
 * go back and forth, and finishes at the last question. It is a window on a desktop and the whole screen on a phone;
 * the content scrolls.
 * <p>
 * With a {@link TestResult} the answers are recorded as they are given: when the learner leaves a question (to the next
 * or the previous one, or out of the dialog) its answer is written to the result, over the old one if the learner went
 * back to it, and the result is finished when they finish the test. A dialog opened on a result that has answers shows
 * them again and starts at the first question without one, so a test that was left (or a page that was refreshed) can be
 * continued. Without a result nothing is recorded.
 * <p>
 * The answers are given to {@link #setOnFinish(Consumer)}; judging them is up to the caller.
 */
@Configurable
public class TestDialog extends Dialog {

    /**
     * What the learner answered to a question.
     *
     * @param answer   see {@link TestQuestionHandler#getAnswer()}, null when the question was left unanswered or the
     *                 learner said they don't know
     * @param dontKnow the learner ticked that they don't know the answer, whatever the question shows then is ignored
     */
    public record Answer(TestQuestion question, String answer, boolean dontKnow) {
    }

    /** Narrower screens are phones, see {@code Main}. */
    private static final int PHONE_WIDTH = 768;

    @Autowired
    private Localization loc;

    @Autowired
    private TestQuestionService questionService;

    @Autowired
    private TestQuestionGroupService groupService;

    @Autowired
    private TestQuestionFactoryRegistry questionFactories;

    @Autowired
    private TestQuestionGroupFactoryRegistry groupFactories;

    @Autowired
    private TestQuestionResultService questionResultService;

    @Autowired
    private TestResultService resultService;

    private static final Logger log = LoggerFactory.getLogger(TestDialog.class);

    private final Test test;
    // null when nothing is recorded
    private final TestResult result;
    private Consumer<List<Answer>> onFinish = answers -> {
    };

    private final List<TestQuestion> questions = new ArrayList<>();
    // null where the question can't be shown
    private final List<TestQuestionHandler<?>> handlers = new ArrayList<>();
    private final List<Component> views = new ArrayList<>();
    private final List<Checkbox> dontKnowBoxes = new ArrayList<>();
    // whether a recorded answer was there when the dialog was opened
    private final List<Boolean> answered = new ArrayList<>();
    // what the groups show, by the id of the group, null for a group that shows nothing
    private final Map<Long, Component> groupViews = new HashMap<>();

    private int current;
    private Span position;
    private ProgressBar progress;
    private Div groupArea;
    private Div questionArea;
    private Button previousButton;
    private Button nextButton;

    public TestDialog(Test test) {
        this(test, null);
    }

    /**
     * @param result the attempt the answers are recorded to (and read from), null to record nothing
     */
    public TestDialog(Test test, TestResult result) {
        this.test = test;
        this.result = result;
    }

    /**
     * Called when the dialog is closed, however: the test was finished or left. The result is up to date by then.
     */
    public void setOnClose(Runnable onClose) {
        addOpenedChangeListener(event -> {
            if (!event.isOpened()) {
                onClose.run();
            }
        });
    }

    /**
     * Called with the answers, one for every question in the order of the test, when the learner finishes the test.
     * The dialog is closed by then.
     */
    public void setOnFinish(Consumer<List<Answer>> onFinish) {
        this.onFinish = onFinish;
    }

    public void create() throws Exception {
        sizeForScreen();
        setModality(ModalityMode.STRICT);
        setCloseOnEsc(false);
        setCloseOnOutsideClick(false);
        setDraggable(false);
        setResizable(false);
        setHeaderTitle(test.getName() != null ? test.getName() : loc.getValue(L.LABEL_LEVEL_TEST));

        Button closeButton = new Button(VaadinIcon.CLOSE.create(), event -> ConfirmDialog.show(
                loc.getValue(result == null ? L.MSG_CONFIRM_LEAVE_TEST : L.MSG_CONFIRM_LEAVE_RECORDED_TEST), () -> {
                    recordCurrent();
                    close();
                }));
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        closeButton.setTooltipText(loc.getValue(L.LABEL_CLOSE));
        getHeader().add(closeButton);

        loadQuestions();

        position = new Span();
        progress = new ProgressBar();
        progress.setMin(0);
        progress.setMax(Math.max(1, questions.size()));
        groupArea = new Div();
        groupArea.addClassName(CssStyles.FULL_WIDTH);
        questionArea = new Div();
        questionArea.addClassName(CssStyles.FULL_WIDTH);

        VerticalLayout content = new VerticalLayout(position, progress, groupArea, questionArea);
        content.setPadding(false);
        content.setSpacing(true);
        add(content);

        previousButton = new Button(loc.getValue(L.LABEL_PREVIOUS), VaadinIcon.ARROW_LEFT.create(), event -> {
            recordCurrent();
            show(current - 1);
        });
        previousButton.addClassName(CssStyles.TEST_NAV_BUTTON);
        nextButton = new Button(loc.getValue(L.LABEL_NEXT), VaadinIcon.ARROW_RIGHT.create(), event -> next());
        nextButton.setIconAfterText(true);
        nextButton.addClassName(CssStyles.TEST_NAV_BUTTON);
        nextButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Div footer = new Div(previousButton, nextButton);
        footer.addClassName(CssStyles.TEST_FOOTER);
        getFooter().add(footer);

        show(firstUnanswered());
    }

    /**
     * The whole screen on a phone (the same width rule as the choice of the workspace), a window otherwise. The window
     * is larger than a small desktop screen can be, the dialog keeps itself inside of the screen.
     */
    private void sizeForScreen() {
        boolean phone = false;
        UI ui = UI.getCurrent();
        if (ui != null) {
            ExtendedClientDetails details = ui.getPage().getExtendedClientDetails();
            phone = details != null && details.getBodyClientWidth() < PHONE_WIDTH;
        }
        setWidth(phone ? "100%" : "900px");
        setHeight(phone ? "100%" : "760px");
    }

    private void loadQuestions() throws Exception {
        questions.addAll(questionService.findByTest(test));
        Map<Long, TestQuestionGroup> groups = new HashMap<>();
        for (TestQuestionGroup group : groupService.findByTest(test)) {
            groups.put(group.getId(), group);
        }

        // the answers recorded already, by the id of the question
        Map<Long, TestQuestionResult> recorded = new HashMap<>();
        if (result != null) {
            for (TestQuestionResult answer : questionResultService.findByTestResult(result)) {
                if (answer.getQuestion() != null) {
                    recorded.put(answer.getQuestion().getId(), answer);
                }
            }
        }

        for (TestQuestion question : questions) {
            TestQuestionFactory factory = questionFactories.getFactory(question);
            TestQuestionHandler<?> handler = factory == null ? null : factory.createHandler(test, question);
            handlers.add(handler);
            Component view = handler == null ? unavailable() : handler.render();
            views.add(view);
            // the question can't be answered when the learner doesn't know it
            Checkbox dontKnow = new Checkbox(loc.getValue(L.LABEL_DONT_KNOW));
            dontKnow.addValueChangeListener(event -> view.getElement().setEnabled(!event.getValue()));
            dontKnowBoxes.add(dontKnow);

            TestQuestionResult answer = recorded.get(question.getId());
            if (answer != null) {
                if (handler != null) {
                    handler.setAnswer(answer.getAnswer());
                }
                dontKnow.setValue(answer.isDontKnow());
            }
            answered.add(answer != null && (answer.getAnswer() != null || answer.isDontKnow()));

            Long groupId = question.getQuestionGroup() == null ? null : question.getQuestionGroup().getId();
            if (groupId != null && !groupViews.containsKey(groupId) && groups.containsKey(groupId)) {
                groupViews.put(groupId, renderGroup(groups.get(groupId)));
            }
        }
    }

    private Component renderGroup(TestQuestionGroup group) {
        TestQuestionGroupFactory factory = groupFactories.getFactory(group);
        TestQuestionGroupHandler<?> handler = factory == null ? null : factory.createHandler(test, group);
        return handler == null ? null : handler.render();
    }

    private Component unavailable() {
        Div notice = new Div(loc.getValue(L.MSG_TEST_QUESTION_UNAVAILABLE));
        notice.addClassNames(CssStyles.NOTICE_WARNING, CssStyles.WHITE_SPACE_NORMAL);
        return notice;
    }

    private void show(int index) {
        if (questions.isEmpty()) {
            position.setText(loc.getValue(L.MSG_TEST_EMPTY));
            previousButton.setEnabled(false);
            nextButton.setEnabled(false);
            return;
        }
        current = Math.max(0, Math.min(index, questions.size() - 1));

        position.setText(String.format(loc.getValue(L.MSG_QUESTION_OF), current + 1, questions.size()));
        progress.setValue(current + 1);

        groupArea.removeAll();
        TestQuestion question = questions.get(current);
        Long groupId = question.getQuestionGroup() == null ? null : question.getQuestionGroup().getId();
        Component group = groupId == null ? null : groupViews.get(groupId);
        if (group != null) {
            groupArea.add(group);
        }
        questionArea.removeAll();
        questionArea.add(views.get(current), dontKnowBoxes.get(current));

        boolean last = current == questions.size() - 1;
        previousButton.setEnabled(current > 0);
        nextButton.setText(loc.getValue(last ? L.LABEL_FINISH : L.LABEL_NEXT));
        nextButton.setIcon(last ? VaadinIcon.CHECK.create() : VaadinIcon.ARROW_RIGHT.create());
    }

    private void next() {
        recordCurrent();
        if (current < questions.size() - 1) {
            show(current + 1);
            return;
        }
        long unanswered = answers().stream().filter(answer -> answer.answer() == null && !answer.dontKnow()).count();
        if (unanswered > 0) {
            ConfirmDialog.show(String.format(loc.getValue(L.MSG_CONFIRM_FINISH_UNANSWERED), unanswered), this::finish);
        } else {
            finish();
        }
    }

    private List<Answer> answers() {
        List<Answer> answers = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            TestQuestionHandler<?> handler = handlers.get(i);
            boolean dontKnow = dontKnowBoxes.get(i).getValue();
            answers.add(new Answer(questions.get(i), dontKnow || handler == null ? null : handler.getAnswer(), dontKnow));
        }
        return answers;
    }

    private void finish() {
        List<Answer> answers = answers();
        if (result != null) {
            try {
                // the last answer was recorded when Finish was clicked, the others when their questions were left
                result.setFinished(true);
                resultService.save(result);
            } catch (Exception e) {
                UIUtils.internalServerError(loc, e);
                return;
            }
        }
        close();
        onFinish.accept(answers);
    }

    /**
     * The index of the first question without an answer, the last one when all have one: where the learner continues.
     */
    private int firstUnanswered() {
        for (int i = 0; i < answered.size(); i++) {
            if (!answered.get(i)) {
                return i;
            }
        }
        return Math.max(0, questions.size() - 1);
    }

    /**
     * Writes the answer of the question that is shown to the result, over the old one. A question that can't be shown
     * has no answer. A failure is shown, the learner can go on.
     */
    private void recordCurrent() {
        if (result == null || questions.isEmpty() || handlers.get(current) == null) {
            return;
        }
        try {
            boolean dontKnow = dontKnowBoxes.get(current).getValue();
            questionResultService.record(result, questions.get(current), dontKnow ? null : handlers.get(current).getAnswer(), dontKnow);
        } catch (Exception e) {
            log.warn("The answer to the question {} could not be recorded", current + 1, e);
            UIUtils.internalServerError(loc, e);
        }
    }
}
