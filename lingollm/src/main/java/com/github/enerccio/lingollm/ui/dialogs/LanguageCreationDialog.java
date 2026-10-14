package com.github.enerccio.lingollm.ui.dialogs;

import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.ui.widgets.LanguageItem;
import com.github.enerccio.lingollm.ui.widgets.Notification;
import com.github.enerccio.lingollm.utils.UIUtils;
import com.google.gson.Gson;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.util.List;

/**
 * Creates a language profile from a language template, and lists the profiles the user already has. Shown without
 * the possibility to cancel at the first start, when the user has no profile to work with.
 */
@Configurable
public class LanguageCreationDialog extends Dialog {

    private static final Gson gson = new Gson();

    @Autowired
    private LanguageService languageService;

    @Autowired
    private Localization loc;

    private boolean firstTime;
    private LanguageCreated onSave;

    private Grid<Language> languageGrid;
    private ComboBox<Languages> languageTemplateCombo;
    private TextField nameField;
    private TextArea noteField;

    public void create() {
        setCloseOnEsc(!firstTime);
        setCloseOnOutsideClick(!firstTime);
        setWidth("650px");
        setHeight("600px");

        setHeaderTitle(loc.getValue(L.LABEL_LANGUAGE_CREATOR_TITLE));

        VerticalLayout mainLayout = new VerticalLayout();
        mainLayout.setSizeFull();
        mainLayout.setPadding(true);
        mainLayout.setSpacing(true);

        languageGrid = new Grid<>(Language.class, false);
        languageGrid.setWidthFull();
        languageGrid.setHeight("220px");
        languageGrid.addComponentColumn(profile -> LanguageItem.create(loc, profile.getLanguage()))
                .setHeader(loc.getValue(L.LABEL_TEMPLATE))
                .setAutoWidth(true);
        languageGrid.addColumn(Language::getNameOverride)
                .setHeader(loc.getValue(L.LABEL_PROFILE_NAME))
                .setAutoWidth(true);
        languageGrid.addColumn(Language::getNote)
                .setHeader(loc.getValue(L.LABEL_NOTE));
        mainLayout.add(languageGrid);

        FormLayout formLayout = new FormLayout();
        formLayout.setWidthFull();

        languageTemplateCombo = new ComboBox<>(loc.getValue(L.LABEL_LANGUAGE_TEMPLATE));
        languageTemplateCombo.setItems(Languages.values());
        languageTemplateCombo.setItemLabelGenerator(lang -> loc.getValue(loc.getLanguageName(lang)));
        languageTemplateCombo.setRenderer(new ComponentRenderer<>(template -> LanguageItem.create(loc, template)));
        languageTemplateCombo.setRequired(true);

        nameField = new TextField(loc.getValue(L.LABEL_PROFILE_NAME));
        nameField.setRequired(true);

        noteField = new TextArea(loc.getValue(L.LABEL_NOTE));
        noteField.setWidthFull();

        // the name of the profile starts as the name of the template
        languageTemplateCombo.addValueChangeListener(event -> {
            Languages selected = event.getValue();
            nameField.setValue(selected != null ? loc.getValue(loc.getLanguageName(selected)) : "");
        });

        formLayout.add(languageTemplateCombo, nameField, noteField);
        formLayout.setColspan(noteField, 2);
        mainLayout.add(formLayout);
        add(mainLayout);

        HorizontalLayout footerLayout = new HorizontalLayout();
        footerLayout.setWidthFull();
        footerLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        if (!firstTime) {
            footerLayout.add(new Button(loc.getValue(L.LABEL_CANCEL), event -> close()));
        }

        Button saveButton = new Button(loc.getValue(L.LABEL_OK), event -> save());
        saveButton.setThemeName("primary");
        footerLayout.add(saveButton);
        getFooter().add(footerLayout);

        refreshGrid();
    }

    private void refreshGrid() {
        try {
            List<Language> languages = languageService.findAllForUser();
            languageGrid.setItems(languages);
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
    }

    private void save() {
        Languages template = languageTemplateCombo.getValue();
        String name = nameField.getValue();
        String note = noteField.getValue();

        if (template == null || StringUtils.isBlank(name)) {
            Notification.warning(loc.getValue(L.MSG_VALIDATION_FAILED_CANT_SAVE));
            return;
        }

        try {
            Language language = new Language();
            language.setLanguage(template);
            language.setNameOverride(name.trim());
            language.setNote(note != null ? note.trim() : null);
            language.setDefaults(gson.toJson(template.defaults));

            Language savedLanguage = languageService.save(language);

            close();

            if (onSave != null) {
                onSave.onLanguageCreated(savedLanguage);
            }
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
    }

    public void setFirstTime(boolean firstTime) {
        this.firstTime = firstTime;
    }

    public void setOnSave(LanguageCreated onSave) {
        this.onSave = onSave;
    }

    public interface LanguageCreated {
        void onLanguageCreated(Language language);
    }
}
