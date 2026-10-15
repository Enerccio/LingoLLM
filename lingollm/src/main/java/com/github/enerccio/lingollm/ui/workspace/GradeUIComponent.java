package com.github.enerccio.lingollm.ui.workspace;

import com.flowingcode.vaadin.addons.fontawesome.FontAwesome.Solid;
import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.domain.collections.CEFR;
import com.github.enerccio.lingollm.domain.collections.TestType;
import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestResult;
import com.github.enerccio.lingollm.domain.model.impl.settings.UserSetting;
import com.github.enerccio.lingollm.domain.service.AIService;
import com.github.enerccio.lingollm.domain.service.CancellationToken;
import com.github.enerccio.lingollm.domain.service.InferenceErrors;
import com.github.enerccio.lingollm.domain.service.LanguageDefinitionService.TestCallback;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.github.enerccio.lingollm.domain.service.SettingService;
import com.github.enerccio.lingollm.domain.service.TestResultService;
import com.github.enerccio.lingollm.domain.service.TestService;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.ui.components.ThreadCopyRequestAttributes;
import com.github.enerccio.lingollm.ui.components.ThreadCopyRequestAttributes.InRequestScope;
import com.github.enerccio.lingollm.ui.dialogs.ConfirmDialog;
import com.github.enerccio.lingollm.ui.dialogs.ListSelectDialog;
import com.github.enerccio.lingollm.ui.dialogs.TestDialog;
import com.github.enerccio.lingollm.ui.dialogs.TestGenerationDialog;
import com.github.enerccio.lingollm.ui.widgets.Notification;
import com.github.enerccio.lingollm.utils.UIUtils;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.util.List;

/**
 * The level of the language profile: the CEFR level the user says they have and their impressions (what they can and
 * cannot do). The user is led to take the grade test, which measures the level better than a guess; the fields are for
 * those who are sure. A profile without a level has all other tabs of {@link LanguageUIBase} frozen until this is saved.
 * <p>
 * Taking the test has the model write it, batch by batch, which takes a while: a modal {@link TestGenerationDialog}
 * shows how many questions are written and stops the generation. The written test is taken in a {@link TestDialog}, which
 * records the answers as they are given; a grade test that was left unfinished is continued or discarded with the two
 * buttons that replace the one that takes the test.
 */
@Configurable
public class GradeUIComponent implements LanguageUIComponent {

    @Autowired
    private Localization loc;

    @Autowired
    private LanguageService languageService;

    @Autowired
    private AIService aiService;

    @Autowired
    private SettingService settingService;

    @Autowired
    private TestService testService;

    @Autowired
    private TestResultService testResultService;

    private static final Logger log = LoggerFactory.getLogger(GradeUIComponent.class);

    private final LanguageUIBase owner;

    private ComboBox<CEFR> gradeCombo;
    private TextArea impressionsField;
    private Span requiredHint;
    private Button takeTestButton;
    private Button continueTestButton;
    private Button discardTestButton;

    // set while the model writes the test; the number tells the answer of a stopped generation from the current one
    private CancellationToken generation;
    private TestGenerationDialog generationDialog;
    private int generationNumber;

    public GradeUIComponent(LanguageUIBase owner) {
        this.owner = owner;
    }

    @Override
    public String getName() {
        return loc.getValue(L.LABEL_LEVEL);
    }

    @Override
    public Component create() {
        VerticalLayout form = new VerticalLayout();
        form.addClassName(CssStyles.GRADE_FORM);
        form.setPadding(true);
        form.setSpacing(true);

        Div recommendation = new Div(loc.getValue(L.MSG_GRADE_TEST_RECOMMENDED));
        recommendation.addClassNames(CssStyles.NOTICE_WARNING, CssStyles.WHITE_SPACE_NORMAL);

        requiredHint = new Span(owner.getGradeRequiredText());
        requiredHint.addClassNames(CssStyles.HINT_TEXT, CssStyles.WHITE_SPACE_NORMAL);

        gradeCombo = new ComboBox<>(loc.getValue(L.LABEL_LEVEL));
        gradeCombo.addClassName(CssStyles.GRADE_COMBO);
        gradeCombo.setItems(CEFR.values());
        gradeCombo.setItemLabelGenerator(level -> loc.getValue(loc.getCefrLevel(level)));
        gradeCombo.setAllowCustomValue(false);

        takeTestButton = new Button(loc.getValue(L.LABEL_TAKE_GRADE_TEST), Solid.ROBOT.create(),
                event -> takeGradeTest());
        takeTestButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        takeTestButton.addClassName(CssStyles.FLEX_SHRINK_0);

        // an unfinished grade test is continued or thrown away, the button that starts a new one is not there then
        continueTestButton = new Button(loc.getValue(L.LABEL_CONTINUE_GRADE_TEST), Solid.PLAY.create(),
                event -> continueGradeTest());
        continueTestButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        continueTestButton.addClassName(CssStyles.FLEX_SHRINK_0);
        discardTestButton = new Button(loc.getValue(L.LABEL_DISCARD_GRADE_TEST), Solid.TRASH.create(),
                event -> ConfirmDialog.show(loc.getValue(L.MSG_CONFIRM_DISCARD_GRADE_TEST), this::discardUnfinished));
        discardTestButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        discardTestButton.addClassName(CssStyles.FLEX_SHRINK_0);

        Div gradeRow = new Div(gradeCombo, takeTestButton, continueTestButton, discardTestButton);
        gradeRow.addClassName(CssStyles.GRADE_ROW);

        impressionsField = new TextArea(loc.getValue(L.LABEL_YOUR_IMPRESSIONS));
        impressionsField.setWidthFull();
        impressionsField.setMinRows(5);

        Button saveButton = new Button(loc.getValue(L.LABEL_SAVE), event -> save());

        form.add(recommendation, requiredHint, gradeRow, impressionsField, saveButton);

        // the form scrolls on its own when a phone shows the keyboard
        Div root = new Div(form);
        root.addClassNames(CssStyles.FULL_SIZE, CssStyles.OVERFLOW_AUTO);

        refresh();
        return root;
    }

    @Override
    public void refresh() {
        if (gradeCombo == null) {
            return;
        }
        Language language = owner.getLanguage();
        gradeCombo.setValue(language != null ? language.getUserGrade() : null);
        impressionsField.setValue(language != null && language.getUserGradeFineDetails() != null
                ? language.getUserGradeFineDetails() : "");
        requiredHint.setVisible(owner.isGradeMissing());
        refreshTestButtons();
    }

    /**
     * Shows the button that takes the test, or the two that continue and discard the grade test the user left
     * unfinished (the page may have been refreshed, or the dialog closed).
     */
    private void refreshTestButtons() {
        if (takeTestButton == null) {
            return;
        }
        boolean unfinished = false;
        try {
            Language language = owner.getLanguage();
            unfinished = language != null && testResultService.findUnfinished(language, TestType.GRADE) != null;
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
        takeTestButton.setVisible(!unfinished);
        continueTestButton.setVisible(unfinished);
        discardTestButton.setVisible(unfinished);
    }

    @Override
    public void onEnter() {
    }

    @Override
    public void onLeave() {
    }

    /**
     * The button of the test: starts the generation of the test.
     */
    protected void takeGradeTest() {
        if (generation != null) {
            return;
        }
        try {
            List<AI> models = aiService.findAllForUser();
            if (models.isEmpty()) {
                Notification.warning(loc.getValue(L.MSG_GRADE_TEST_NO_MODEL));
                return;
            }
            AI preferred = findDefaultModel(models);
            if (preferred != null) {
                generate(preferred);
            } else if (models.size() == 1) {
                generate(models.getFirst());
            } else {
                ListSelectDialog<AI> dialog = new ListSelectDialog<>(loc.getValue(L.LABEL_TAKE_GRADE_TEST),
                        loc.getValue(L.LABEL_MODELS), AI::getName, this::generate);
                dialog.create();
                dialog.setItems(models);
                dialog.open();
            }
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
    }

    /**
     * The default model of the user, null when they have none or it is not among the models they can use.
     */
    private AI findDefaultModel(List<AI> models) throws Exception {
        Long id = settingService.getOrCreate(UserSetting.class).getDefaultModel();
        return id == null ? null : models.stream().filter(model -> id.equals(model.getId())).findFirst().orElse(null);
    }

    private void generate(AI ai) {
        UI ui = UI.getCurrent();
        ThreadCopyRequestAttributes attributes = ThreadCopyRequestAttributes.create();
        int number = ++generationNumber;
        log.info("Generation {} of the grade test for {} started with the model {}", number, owner.getLanguage().getUuid(), ai.getName());
        try {
            generationDialog = new TestGenerationDialog(this::stopGeneration);
            generationDialog.create();
            generationDialog.open();
            // the callbacks wait for the session lock, which this click holds, so they come after the token is set
            generation = owner.getDefinition().createGaugeTest(owner.getLanguage(), ai, new TestCallback() {
                @Override
                public void onProgress(int done, int total) {
                    access(ui, attributes, () -> {
                        if (number == generationNumber && generation != null) {
                            generationDialog.setProgress(done, total);
                        }
                    });
                }

                @Override
                public void onTest(Test test) {
                    log.info("Generation {} of the grade test finished: test {}", number, test.getUuid());
                    access(ui, attributes, () -> {
                        if (number != generationNumber || generation == null) {
                            log.warn("Generation {} is not the current one (current {}, running {}), the test {} is dropped",
                                    number, generationNumber, generation != null, test.getUuid());
                            discard(test);
                            return;
                        }
                        stopGenerating();
                        takeTest(test);
                    });
                }

                @Override
                public void onError(Throwable error) {
                    log.warn("Generation {} of the grade test failed: {}", number, error.getMessage());
                    access(ui, attributes, () -> {
                        if (number != generationNumber || generation == null) {
                            log.warn("Generation {} is not the current one, its failure is not shown", number);
                            return;
                        }
                        stopGenerating();
                        // stays until the user closes it: it says what the model got wrong
                        Notification.error(String.format(loc.getValue(L.MSG_GRADE_TEST_FAILED),
                                InferenceErrors.messageOf(loc, error)), Notification.DURATION_INFINITE);
                    });
                }

                @Override
                public void onCancel() {
                    log.info("Generation {} of the grade test was cancelled", number);
                    access(ui, attributes, () -> {
                        if (number == generationNumber && generation != null) {
                            stopGenerating();
                        }
                    });
                }
            });
        } catch (Exception e) {
            stopGenerating();
            UIUtils.internalServerError(loc, e);
        }
    }

    private void stopGeneration() {
        CancellationToken token = generation;
        stopGenerating();
        if (token != null) {
            token.cancel();
        }
    }

    /**
     * Closes the progress dialog. Does nothing twice.
     */
    private void stopGenerating() {
        generation = null;
        if (generationDialog != null) {
            generationDialog.close();
            generationDialog = null;
        }
    }

    /**
     * The test that was just written is started: its result is created at once, the answers are recorded as they are
     * given.
     */
    private void takeTest(Test test) {
        try {
            openTest(test, testResultService.start(test));
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
    }

    /**
     * Continues the grade test the user left unfinished, from the first question they did not answer.
     */
    private void continueGradeTest() {
        try {
            TestResult unfinished = testResultService.findUnfinished(owner.getLanguage(), TestType.GRADE);
            if (unfinished == null) {
                refreshTestButtons();
                return;
            }
            openTest(testService.find(unfinished.getTest()), unfinished);
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
    }

    /**
     * Throws the unfinished grade test away with its questions and answers.
     */
    private void discardUnfinished() {
        try {
            TestResult unfinished = testResultService.findUnfinished(owner.getLanguage(), TestType.GRADE);
            if (unfinished != null) {
                testService.delete(testService.find(unfinished.getTest()), true);
            }
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
        refreshTestButtons();
    }

    /**
     * Opens the test. For now the answers are only collected: judging them and setting the level comes later.
     */
    private void openTest(Test test, TestResult result) throws Exception {
        TestDialog dialog = new TestDialog(test, result);
        dialog.setOnFinish(answers -> Notification.warning(loc.getValue(L.MSG_GRADE_TEST_NOT_AVAILABLE)));
        // closed unfinished: the buttons offer to continue it
        dialog.setOnClose(this::refreshTestButtons);
        dialog.create();
        dialog.open();
        refreshTestButtons();
    }

    /**
     * A test that was written after the user stopped the generation is of no use to anybody.
     */
    private void discard(Test test) {
        try {
            testService.delete(test, true);
        } catch (Exception e) {
            log.warn("The test of the stopped generation could not be deleted", e);
        }
    }

    /**
     * Runs the change of the UI from the thread of the inference, in the session of the user.
     */
    private void access(UI ui, ThreadCopyRequestAttributes attributes, Runnable change) {
        try {
            ui.access(() -> {
                try (InRequestScope _ = new InRequestScope(attributes)) {
                    change.run();
                } catch (Exception e) {
                    UIUtils.internalServerError(loc, e);
                }
            });
        } catch (UIDetachedException e) {
            log.warn("The UI is gone, the result of the grade test generation is not shown");
        }
    }

    private void save() {
        CEFR grade = gradeCombo.getValue();
        if (grade == null) {
            Notification.warning(loc.getValue(L.MSG_VALIDATION_FAILED_CANT_SAVE));
            return;
        }

        try {
            // the profile is loaded again, so a stale copy does not overwrite what changed meanwhile
            Language language = languageService.find(owner.getLanguage());
            language.setUserGrade(grade);
            language.setUserGradeFineDetails(StringUtils.trimToNull(impressionsField.getValue()));
            Language saved = languageService.save(language);

            owner.gradeSaved(saved);
            refresh();
            Notification.success(loc.getValue(L.MSG_GRADE_SAVED));
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
    }

}
