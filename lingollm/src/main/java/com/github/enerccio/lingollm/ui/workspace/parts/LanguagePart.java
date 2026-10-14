package com.github.enerccio.lingollm.ui.workspace.parts;

import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.model.impl.settings.UserSetting;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.github.enerccio.lingollm.domain.service.SettingService;
import com.github.enerccio.lingollm.domain.traits.*;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.ui.dialogs.LanguageCreationDialog;
import com.github.enerccio.lingollm.ui.widgets.LanguageItem;
import com.github.enerccio.lingollm.ui.workspace.LanguageUI;
import com.github.enerccio.lingollm.ui.workspace.Workspace;
import com.github.enerccio.lingollm.ui.workspace.WorkspaceComponent;
import com.github.enerccio.lingollm.utils.UIUtils;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.util.List;

/**
 * The part of the workspace for learning: a selector of the language profiles of the user above the UI of the
 * language of the selected profile ({@link LanguageUI}). The profile the user left on is remembered in
 * {@link UserSetting#getLastLanguage()}. A user without a profile is asked to create the first one.
 */
@Configurable
@Extendable
public class LanguagePart implements WorkspaceComponent {

    @Autowired
    private Localization loc;

    @Autowired
    private LanguageService languageService;

    @Autowired
    private SettingService settingService;

    @Autowired
    private Language currentLanguage;

    private final Workspace workspace;

    private ComboBox<Language> languageCombo;
    private Div content;

    private LanguageUI languageUI;
    private Language selectedLanguage;
    private boolean firstTimeAsked;
    // true while the combo is filled by the code, not chosen by the user
    private boolean internalEvent;

    public LanguagePart(Workspace workspace) {
        this.workspace = workspace;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Component.class),
            locals = {@APILocals(name = "layout", type = VerticalLayout.class),
                    @APILocals(name = "header", type = Component.class)})
    @Override
    public Component create() throws Exception {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(true);
        layout.setSpacing(true);

        Component header = createHeader();

        content = new Div();
        content.addClassNames(CssStyles.FULL_WIDTH, CssStyles.MIN_HEIGHT_0);

        layout.add(header, content);
        layout.setFlexGrow(1, content);
        return layout;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Component.class),
            locals = {@APILocals(name = "headerLayout", type = HorizontalLayout.class),
                    @APILocals(name = "addLanguageButton", type = Button.class)})
    private Component createHeader() {
        HorizontalLayout headerLayout = new HorizontalLayout();
        headerLayout.setWidthFull();
        headerLayout.setAlignItems(Alignment.CENTER);

        languageCombo = new ComboBox<>();
        languageCombo.setWidthFull();
        languageCombo.setAllowCustomValue(false);
        languageCombo.setItemLabelGenerator(profile -> LanguageItem.label(loc, profile));
        languageCombo.setRenderer(new ComponentRenderer<>(profile -> LanguageItem.create(loc, profile)));
        languageCombo.addValueChangeListener(event -> {
            if (!internalEvent && event.getValue() != null) {
                selectLanguage(event.getValue(), true);
            }
        });

        Button addLanguageButton = new Button(VaadinIcon.PLUS.create(), event -> openCreationDialog(false));
        addLanguageButton.setTooltipText(loc.getValue(L.LABEL_ADD_LANGUAGE));

        headerLayout.add(languageCombo, addLanguageButton);
        headerLayout.setFlexGrow(1, languageCombo);
        return headerLayout;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "firstTime", type = boolean.class)},
            locals = {@APILocals(name = "dialog", type = LanguageCreationDialog.class)})
    private void openCreationDialog(boolean firstTime) {
        LanguageCreationDialog dialog = new LanguageCreationDialog();
        dialog.setFirstTime(firstTime);
        dialog.setOnSave(created -> selectLanguage(created, true));
        dialog.create();
        dialog.open();
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            locals = {@APILocals(name = "profiles", type = List.class),
                    @APILocals(name = "e", type = Exception.class)})
    @Override
    public void refresh() throws Exception {
        List<Language> profiles = languageService.findAllForUser();

        internalEvent = true;
        try {
            languageCombo.setItems(profiles);
        } finally {
            internalEvent = false;
        }

        if (profiles.isEmpty()) {
            selectedLanguage = null;
            selectSessionLanguage(null);
            showLanguage(null);
            if (!firstTimeAsked) {
                // the initial setup: nothing to learn with before the first profile exists
                firstTimeAsked = true;
                openCreationDialog(true);
            }
            return;
        }

        try {
            selectLanguage(findProfileToOpen(profiles), false);
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
    }

    /**
     * The profile that is open, otherwise the one the user left on, otherwise the first.
     */
    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Language.class),
            parameters = {@APIParameter(name = "profiles", type = List.class)},
            locals = {@APILocals(name = "wanted", type = Long.class)})
    private Language findProfileToOpen(List<Language> profiles) throws Exception {
        Long wanted = selectedLanguage != null ? selectedLanguage.getId()
                : settingService.getOrCreate(UserSetting.class).getLastLanguage();
        return profiles.stream()
                .filter(profile -> profile.getId().equals(wanted))
                .findFirst()
                .orElse(profiles.getFirst());
    }

    /**
     * Opens the language profile.
     *
     * @param remember whether the profile is saved as the one to open at the next login
     */
    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "language", type = Language.class),
                    @APIParameter(name = "remember", type = boolean.class)},
            locals = {@APILocals(name = "userSetting", type = UserSetting.class),
                    @APILocals(name = "profiles", type = List.class),
                    @APILocals(name = "e", type = Exception.class)})
    public void selectLanguage(Language language, boolean remember) {
        try {
            if (remember) {
                UserSetting userSetting = settingService.getOrCreate(UserSetting.class);
                userSetting.setLastLanguage(language.getId());
                settingService.save(userSetting);
            }

            // a profile created just now is not among the items yet
            List<Language> profiles = languageService.findAllForUser();
            internalEvent = true;
            try {
                languageCombo.setItems(profiles);
                languageCombo.setValue(profiles.stream()
                        .filter(profile -> profile.getId().equals(language.getId()))
                        .findFirst().orElse(null));
            } finally {
                internalEvent = false;
            }

            selectSessionLanguage(language);
            boolean sameProfile = selectedLanguage != null && selectedLanguage.getId().equals(language.getId());
            selectedLanguage = language;
            if (!sameProfile || languageUI == null) {
                showLanguage(language);
            } else {
                languageUI.setLanguage(language);
                languageUI.refresh();
            }
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
    }

    /**
     * Makes the profile the one of the session, so what the user creates from now on belongs to it.
     */
    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "language", type = Language.class)})
    private void selectSessionLanguage(Language language) {
        currentLanguage.setId(language != null ? language.getId() : null);
        currentLanguage.setUuid(language != null ? language.getUuid() : null);
        currentLanguage.setLanguage(language != null ? language.getLanguage() : null);
        currentLanguage.setNameOverride(language != null ? language.getNameOverride() : null);
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "language", type = Language.class)},
            locals = {@APILocals(name = "ui", type = LanguageUI.class),
                    @APILocals(name = "component", type = Component.class),
                    @APILocals(name = "e", type = Exception.class)})
    private void showLanguage(Language language) {
        try {
            if (languageUI != null) {
                languageUI.onTabClosed();
            }
            languageUI = null;
            content.removeAll();

            if (language == null || language.getLanguage() == null) {
                return;
            }

            LanguageUI ui = language.getLanguage().create(workspace);
            ui.setLanguage(language);
            Component component = ui.create();
            if (component != null) {
                content.add(component);
            }
            languageUI = ui;
            ui.refresh();
            ui.onTabSwitched();
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class))
    @Override
    public void onTabSwitched() throws Exception {
        if (languageUI != null) {
            languageUI.onTabSwitched();
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class))
    @Override
    public void onTabClosed() throws Exception {
        if (languageUI != null) {
            languageUI.onTabClosed();
        }
    }

}
