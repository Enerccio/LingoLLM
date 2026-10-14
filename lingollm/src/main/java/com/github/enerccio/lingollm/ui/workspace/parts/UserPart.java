package com.github.enerccio.lingollm.ui.workspace.parts;

import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.model.impl.settings.UserSetting;
import com.github.enerccio.lingollm.domain.service.AIService;
import com.github.enerccio.lingollm.domain.service.SettingService;
import com.github.enerccio.lingollm.domain.traits.*;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.ui.widgets.Notification;
import com.github.enerccio.lingollm.ui.workspace.Workspace;
import com.github.enerccio.lingollm.ui.workspace.WorkspaceComponent;
import com.github.enerccio.lingollm.utils.UIUtils;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.accordion.Accordion;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.util.List;

/**
 * The settings of the user: the general ones and a panel for every extension that has settings (extensions fill the
 * accordion by decorating {@link #refresh()} and {@link #save()}).
 */
@Configurable
@Extendable
public class UserPart implements WorkspaceComponent {

    private static final String CHANGE_TRACKED_KEY = UserPart.class.getName() + ".changeTracked";

    @Autowired
    private Localization loc;

    @Autowired
    private SettingService settingService;

    @Autowired
    private AIService aiService;

    private final Workspace workspace;

    private VerticalLayout mainLayout;
    private TabSheet tabSheet;
    protected Span unsavedChangesSpan;
    protected Button discardButton;

    private ComboBox<AI> defaultModelCombo;

    // extensions add their settings here
    private Accordion extensionSettings;

    private UserSetting userSetting;

    // true while refresh() (and the extension decorators around it) fills the fields
    private boolean loading;
    private boolean changed;

    public UserPart(Workspace workspace) {
        this.workspace = workspace;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = String.class))
    public String getName() {
        return loc.getValue(L.LABEL_SETTINGS);
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Component.class),
            locals = {@APILocals(name = "headerLayout", type = Component.class),
                    @APILocals(name = "defaultsFormLayout", type = FormLayout.class)})
    @Override
    public Component create() throws Exception {
        mainLayout = new VerticalLayout();
        mainLayout.setSizeFull();
        mainLayout.setPadding(true);
        mainLayout.setSpacing(true);

        Component headerLayout = createHeader();

        createFields();

        tabSheet = new TabSheet();
        tabSheet.setSizeFull();
        tabSheet.addClassName(CssStyles.MIN_HEIGHT_0);

        FormLayout defaultsFormLayout = new FormLayout();
        defaultsFormLayout.setWidthFull();
        defaultsFormLayout.add(defaultModelCombo);

        tabSheet.add(loc.getValue(L.LABEL_GENERAL_SETTINGS), defaultsFormLayout);

        extensionSettings = new Accordion();
        extensionSettings.setSizeFull();
        tabSheet.add(loc.getValue(L.LABEL_EXTENSION_SETTINGS), extensionSettings);

        mainLayout.add(headerLayout, tabSheet);
        mainLayout.setFlexGrow(1, tabSheet);

        refresh();

        return mainLayout;
    }

    /**
     * The top of the part: the title, the unsaved changes hint and the buttons. {@link #unsavedChangesSpan} and
     * {@link #discardButton} must be created here, the change tracking toggles them.
     */
    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Component.class),
            locals = {@APILocals(name = "headerLayout", type = HorizontalLayout.class),
                    @APILocals(name = "titleSpan", type = Span.class),
                    @APILocals(name = "saveButton", type = Button.class)})
    protected Component createHeader() {
        HorizontalLayout headerLayout = new HorizontalLayout();
        headerLayout.setWidthFull();
        headerLayout.setAlignItems(FlexComponent.Alignment.CENTER);

        Span titleSpan = createTitle();
        unsavedChangesSpan = createUnsavedChangesSpan();

        discardButton = createDiscardButton();
        Button saveButton = createSaveButton();

        headerLayout.add(titleSpan, unsavedChangesSpan, discardButton, saveButton);
        headerLayout.setFlexGrow(1, titleSpan);
        headerLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        return headerLayout;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Span.class),
            locals = {@APILocals(name = "titleSpan", type = Span.class)})
    protected Span createTitle() {
        Span titleSpan = new Span(loc.getValue(L.LABEL_SETTINGS));
        titleSpan.addClassName(CssStyles.SECTION_TITLE);
        return titleSpan;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Span.class),
            locals = {@APILocals(name = "span", type = Span.class)})
    protected Span createUnsavedChangesSpan() {
        Span span = new Span(loc.getValue(L.MSG_UNSAVED_SETTINGS));
        span.addClassName(CssStyles.TEXT_ERROR);
        span.setVisible(false);
        return span;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Button.class),
            locals = {@APILocals(name = "button", type = Button.class)})
    protected Button createDiscardButton() {
        Button button = new Button(loc.getValue(L.LABEL_DISCARD_CHANGES), event -> discard());
        button.setEnabled(false);
        return button;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Button.class),
            locals = {@APILocals(name = "button", type = Button.class)})
    protected Button createSaveButton() {
        Button button = new Button(loc.getValue(L.LABEL_SAVE), event -> save());
        button.setThemeName("primary");
        return button;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class))
    private void createFields() {
        defaultModelCombo = new ComboBox<>(loc.getValue(L.LABEL_DEFAULT_MODEL));
        defaultModelCombo.setWidthFull();
        defaultModelCombo.setItemLabelGenerator(AI::getName);
        defaultModelCombo.setClearButtonVisible(true);
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            locals = {@APILocals(name = "allModels", type = List.class),
                    @APILocals(name = "defaultModelId", type = Long.class),
                    @APILocals(name = "selectedModel", type = AI.class),
                    @APILocals(name = "e", type = Exception.class)})
    @Override
    public void refresh() throws Exception {
        loading = true;
        try {
            userSetting = settingService.getOrCreate(UserSetting.class);

            List<AI> allModels = aiService.findAllForUser();
            defaultModelCombo.setItems(allModels);

            Long defaultModelId = userSetting.getDefaultModel();
            if (defaultModelId != null) {
                AI selectedModel = aiService.find(defaultModelId);
                if (selectedModel != null && !selectedModel.isDeleted()) {
                    defaultModelCombo.setValue(selectedModel);
                } else {
                    defaultModelCombo.setValue(null);
                }
            } else {
                defaultModelCombo.setValue(null);
            }
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        } finally {
            finishLoading();
        }
    }

    /**
     * Extension decorators fill their settings forms after refresh() returns, so the fields are tracked (and the
     * loaded values accepted as unchanged) only before the response is sent.
     */
    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class))
    private void finishLoading() {
        mainLayout.getElement().getNode().runWhenAttached(ui -> ui.beforeClientResponse(mainLayout, ctx -> {
            trackChanges(mainLayout);
            // a TabSheet keeps only the content of the selected tab in the component tree
            for (int i = 0; i < tabSheet.getTabCount(); i++) {
                trackChanges(tabSheet.getComponent(tabSheet.getTabAt(i)));
            }
            loading = false;
            setChanged(false);
        }));
    }

    /**
     * Listens to every field of the part, including the extension settings forms. Any change outside of refresh()
     * locks the workspace navigation until the settings are saved or discarded.
     */
    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "component", type = Component.class)},
            locals = {@APILocals(name = "field", type = HasValue.class)})
    private void trackChanges(Component component) {
        if (component instanceof HasValue<?, ?> field && ComponentUtil.getData(component, CHANGE_TRACKED_KEY) == null) {
            ComponentUtil.setData(component, CHANGE_TRACKED_KEY, Boolean.TRUE);
            field.addValueChangeListener(e -> {
                if (!loading) {
                    setChanged(true);
                }
            });
        }
        component.getChildren().forEach(this::trackChanges);
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "changed", type = boolean.class)})
    private void setChanged(boolean changed) {
        if (this.changed == changed) {
            return;
        }
        this.changed = changed;
        unsavedChangesSpan.setVisible(changed);
        discardButton.setEnabled(changed);
        workspace.setNavigationLocked(changed);
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            locals = {@APILocals(name = "e", type = Exception.class)})
    private void discard() {
        try {
            refresh();
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            locals = {@APILocals(name = "selectedModel", type = AI.class),
                    @APILocals(name = "e", type = Exception.class)})
    private void save() {
        if (userSetting == null) {
            return;
        }

        try {
            AI selectedModel = defaultModelCombo.getValue();
            userSetting.setDefaultModel(selectedModel != null ? selectedModel.getId() : null);

            settingService.save(userSetting);
            setChanged(false);
            Notification.success(loc.getValue(L.MSG_SETTINGS_SAVED));
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class))
    @Override
    public void onTabSwitched() throws Exception {
        refresh();
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class))
    @Override
    public void onTabClosed() throws Exception {

    }
}
