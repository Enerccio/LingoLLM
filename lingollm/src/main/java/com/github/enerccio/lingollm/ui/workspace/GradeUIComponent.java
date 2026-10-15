package com.github.enerccio.lingollm.ui.workspace;

import com.flowingcode.vaadin.addons.fontawesome.FontAwesome.Solid;
import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.domain.collections.CEFR;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.ui.widgets.Notification;
import com.github.enerccio.lingollm.utils.UIUtils;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.util.Arrays;

/**
 * The level of the language profile: the CEFR level the user says they have and their impressions (what they can and
 * cannot do). The user is led to take the grade test, which measures the level better than a guess; the fields are for
 * those who are sure. A profile without a level has all other tabs of {@link LanguageUIBase} frozen until this is saved.
 */
@Configurable
public class GradeUIComponent implements LanguageUIComponent {

    @Autowired
    private Localization loc;

    @Autowired
    private LanguageService languageService;

    private final LanguageUIBase owner;

    private ComboBox<CEFR> gradeCombo;
    private TextArea impressionsField;
    private Span requiredHint;

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

        Button takeTestButton = new Button(loc.getValue(L.LABEL_TAKE_GRADE_TEST), Solid.ROBOT.create(),
                event -> takeGradeTest());
        takeTestButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        takeTestButton.addClassName(CssStyles.FLEX_SHRINK_0);

        Div gradeRow = new Div(gradeCombo, takeTestButton);
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
    }

    @Override
    public void onEnter() {
    }

    @Override
    public void onLeave() {
    }

    /**
     * Starts the test that gauges the level. There is no screen for taking a test yet, so for now it only says so.
     */
    protected void takeGradeTest() {
        Notification.warning(loc.getValue(L.MSG_GRADE_TEST_NOT_AVAILABLE));
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
