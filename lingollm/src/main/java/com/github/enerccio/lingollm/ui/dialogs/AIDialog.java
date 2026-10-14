package com.github.enerccio.lingollm.ui.dialogs;

import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.domain.collections.AIType;
import com.github.enerccio.lingollm.domain.collections.ReasoningEffort;
import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.model.impl.OpenAICompatible;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.service.AIService;
import com.github.enerccio.lingollm.domain.service.InferenceErrors;
import com.github.enerccio.lingollm.domain.service.InferenceServices;
import com.github.enerccio.lingollm.domain.service.impl.inference.OpenAICompatibleInferenceService;
import com.github.enerccio.lingollm.domain.traits.*;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.ui.widgets.Notification;
import com.github.enerccio.lingollm.ui.widgets.ResizableTextArea;
import com.github.enerccio.lingollm.utils.UIUtils;
import com.google.gson.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.util.ArrayList;
import java.util.List;

@Configurable
@Extendable
public class AIDialog extends Dialog {
    private static final Gson gson = new GsonBuilder().create();
    private static final int DEFAULT_MAX_CONTEXT = 16384;
    private static final int DEFAULT_MAX_RESPONSE = 2048;

    @Autowired
    protected Localization loc;

    @Autowired
    private AIService aiService;

    @Autowired
    private InferenceServices inferenceServices;

    @Autowired
    private User currentUser;

    private AI ai;
    private Runnable onSave;

    // Header fields (above tabs)
    private TextField nameField;
    private ComboBox<AIType> typeCombo;
    private Checkbox sharedCheckbox;

    // General fields (in tab 1)
    private IntegerField maxContextField;
    private IntegerField maxResponseField;
    private Checkbox needsJailbreakCheckbox;
    private TextArea jailbreakField;
    private Checkbox enabledReasoningCheckbox;
    private ComboBox<ReasoningEffort> reasoningEffortCombo;

    // Per type fields (in tab 2)
    private FormLayout dynamicFormLayout;
    private TextField urlField;
    private PasswordField apiKeyField;
    private Button resetApiKeyButton;
    private ComboBox<String> modelCombo;
    private Button refreshModelsButton;
    private TextArea additionalParametersField;
    private IntegerField requestTimeoutField;
    private IntegerField maxRetriesField;
    private Button testConnectionButton;

    public AIDialog() {
        this(null);
    }

    public AIDialog(AI ai) {
        this.ai = ai;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            locals = {@APILocals(name = "mainLayout", type = VerticalLayout.class),
                    @APILocals(name = "baseFormLayout", type = FormLayout.class),
                    @APILocals(name = "tabSheet", type = TabSheet.class),
                    @APILocals(name = "generalFormLayout", type = FormLayout.class),
                    @APILocals(name = "footerLayout", type = HorizontalLayout.class),
                    @APILocals(name = "cancelButton", type = Button.class),
                    @APILocals(name = "saveButton", type = Button.class)})
    public void create() {
        boolean isEdit = ai != null && ai.getId() != null;
        setHeaderTitle(isEdit ? loc.getValue(L.LABEL_EDIT_AI) : loc.getValue(L.LABEL_NEW_AI));
        setWidth("600px");
        setHeight("820px");
        setCloseOnEsc(false);
        setCloseOnOutsideClick(false);

        createFields();

        VerticalLayout mainLayout = new VerticalLayout();
        mainLayout.setSizeFull();
        mainLayout.setPadding(false);
        mainLayout.setSpacing(false);

        FormLayout baseFormLayout = new FormLayout();
        baseFormLayout.setWidthFull();
        baseFormLayout.add(nameField, typeCombo);
        if (sharedCheckbox.isVisible()) {
            baseFormLayout.add(sharedCheckbox, 2);
        }

        mainLayout.add(baseFormLayout);

        TabSheet tabSheet = new TabSheet();
        tabSheet.setSizeFull();
        tabSheet.addClassName(CssStyles.MIN_HEIGHT_0);

        FormLayout generalFormLayout = new FormLayout();
        generalFormLayout.setWidthFull();
        generalFormLayout.addClassName(CssStyles.FORM_PADDING_BOTTOM);
        generalFormLayout.add(maxContextField, maxResponseField);
        generalFormLayout.add(needsJailbreakCheckbox, jailbreakField);
        generalFormLayout.setColspan(jailbreakField, 2);
        generalFormLayout.add(enabledReasoningCheckbox, reasoningEffortCombo);

        tabSheet.add(loc.getValue(L.LABEL_GENERAL_SETTINGS), generalFormLayout);

        dynamicFormLayout = new FormLayout();
        dynamicFormLayout.setWidthFull();
        dynamicFormLayout.addClassName(CssStyles.FORM_PADDING_BOTTOM);

        tabSheet.add(loc.getValue(L.LABEL_TYPE_SETTINGS), dynamicFormLayout);

        mainLayout.add(tabSheet);
        mainLayout.setFlexGrow(1, tabSheet);

        typeCombo.addValueChangeListener(event -> updateDynamicFields(event.getValue()));

        if (isEdit) {
            populateFields();
        } else {
            typeCombo.setValue(AIType.OPEN_AI_COMPATIBLE);
            maxContextField.setValue(DEFAULT_MAX_CONTEXT);
            maxResponseField.setValue(DEFAULT_MAX_RESPONSE);
            apiKeyField.setEnabled(true);
            resetApiKeyButton.setVisible(false);
            needsJailbreakCheckbox.setValue(false);
            jailbreakField.setEnabled(false);
            enabledReasoningCheckbox.setValue(false);
            reasoningEffortCombo.setEnabled(false);
            reasoningEffortCombo.setValue(ReasoningEffort.NONE);
        }

        add(mainLayout);

        HorizontalLayout footerLayout = new HorizontalLayout();
        footerLayout.setWidthFull();
        footerLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        Button cancelButton = new Button(loc.getValue(L.LABEL_CANCEL), event -> close());
        Button saveButton = new Button(loc.getValue(L.LABEL_OK), event -> save());
        saveButton.setThemeName("primary");

        footerLayout.add(cancelButton, saveButton);
        getFooter().add(footerLayout);
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class))
    private void createFields() {
        nameField = new TextField(loc.getValue(L.LABEL_NAME));
        nameField.setRequired(true);
        nameField.setWidthFull();

        typeCombo = new ComboBox<>(loc.getValue(L.LABEL_TYPE));
        typeCombo.setItems(AIType.values());
        typeCombo.setItemLabelGenerator(type -> loc.getValue(loc.getAIType(type)));
        typeCombo.setRequired(true);
        typeCombo.setWidthFull();

        // only administrators share, the service refuses it for anybody else
        sharedCheckbox = new Checkbox(loc.getValue(L.LABEL_SHARED_WITH_ALL_USERS));
        sharedCheckbox.setVisible(aiService.isAdministrator());

        maxContextField = new IntegerField(loc.getValue(L.LABEL_MAX_CONTEXT));
        maxContextField.setRequired(true);
        maxContextField.setMin(1);
        maxContextField.setHelperText(loc.getValue(L.HELP_AI_MAX_CONTEXT));
        maxContextField.setWidthFull();

        maxResponseField = new IntegerField(loc.getValue(L.LABEL_MAX_RESPONSE));
        maxResponseField.setRequired(true);
        maxResponseField.setMin(1);
        maxResponseField.setHelperText(loc.getValue(L.HELP_AI_MAX_RESPONSE));
        maxResponseField.setWidthFull();

        needsJailbreakCheckbox = new Checkbox(loc.getValue(L.LABEL_NEEDS_JAILBREAK));
        jailbreakField = new TextArea(loc.getValue(L.LABEL_JAILBREAK));
        jailbreakField.setWidthFull();
        ResizableTextArea.install(loc, jailbreakField, "120px");
        jailbreakField.setEnabled(false);
        needsJailbreakCheckbox.addValueChangeListener(event -> jailbreakField.setEnabled(Boolean.TRUE.equals(event.getValue())));

        enabledReasoningCheckbox = new Checkbox(loc.getValue(L.LABEL_ENABLED_REASONING));
        reasoningEffortCombo = new ComboBox<>(loc.getValue(L.LABEL_REASONING_EFFORT));
        reasoningEffortCombo.setItems(ReasoningEffort.values());
        reasoningEffortCombo.setItemLabelGenerator(effort -> loc.getValue(loc.getReasoningEffort(effort)));
        reasoningEffortCombo.setWidthFull();
        reasoningEffortCombo.setEnabled(false);
        enabledReasoningCheckbox.addValueChangeListener(event -> reasoningEffortCombo.setEnabled(Boolean.TRUE.equals(event.getValue())));

        urlField = new TextField(loc.getValue(L.LABEL_URL));
        urlField.setRequired(true);
        urlField.setWidthFull();

        apiKeyField = new PasswordField(loc.getValue(L.LABEL_API_KEY));
        apiKeyField.setWidthFull();

        resetApiKeyButton = new Button(loc.getValue(L.LABEL_RESET), event -> {
            apiKeyField.setEnabled(true);
            apiKeyField.focus();
            resetApiKeyButton.setEnabled(false);
        });

        modelCombo = new ComboBox<>(loc.getValue(L.LABEL_MODEL));
        modelCombo.setRequired(true);
        modelCombo.setWidthFull();
        modelCombo.setAllowCustomValue(true);
        modelCombo.addCustomValueSetListener(event -> {
            modelCombo.setItems(List.of(event.getDetail()));
            modelCombo.setValue(event.getDetail());
        });

        refreshModelsButton = new Button(loc.getValue(L.LABEL_REFRESH), event -> fetchModels());

        additionalParametersField = new TextArea(loc.getValue(L.LABEL_ADDITIONAL_PARAMETERS));
        additionalParametersField.setWidthFull();
        ResizableTextArea.install(loc, additionalParametersField, "120px");

        requestTimeoutField = new IntegerField(loc.getValue(L.LABEL_REQUEST_TIMEOUT));
        requestTimeoutField.setMin(1);
        requestTimeoutField.setHelperText(loc.getValue(L.HELP_AI_REQUEST_TIMEOUT));
        requestTimeoutField.setValue(OpenAICompatibleInferenceService.DEFAULT_TIMEOUT_SECONDS);
        requestTimeoutField.setWidthFull();

        maxRetriesField = new IntegerField(loc.getValue(L.LABEL_MAX_RETRIES));
        maxRetriesField.setMin(0);
        maxRetriesField.setMax(10);
        maxRetriesField.setStepButtonsVisible(true);
        maxRetriesField.setHelperText(loc.getValue(L.HELP_AI_MAX_RETRIES));
        maxRetriesField.setValue(OpenAICompatibleInferenceService.DEFAULT_MAX_RETRIES);
        maxRetriesField.setWidthFull();

        testConnectionButton = new Button(loc.getValue(L.LABEL_TEST_CONNECTION), event -> testConnection());
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "type", type = AIType.class)},
            locals = {@APILocals(name = "apiKeyLayout", type = HorizontalLayout.class),
                    @APILocals(name = "modelLayout", type = HorizontalLayout.class)})
    private void updateDynamicFields(AIType type) {
        dynamicFormLayout.removeAll();
        if (type == AIType.OPEN_AI_COMPATIBLE) {
            HorizontalLayout apiKeyLayout = new HorizontalLayout();
            apiKeyLayout.setWidthFull();
            apiKeyLayout.setAlignItems(FlexComponent.Alignment.BASELINE);
            apiKeyLayout.add(apiKeyField, resetApiKeyButton);
            apiKeyLayout.setFlexGrow(1, apiKeyField);

            HorizontalLayout modelLayout = new HorizontalLayout();
            modelLayout.setWidthFull();
            modelLayout.setAlignItems(FlexComponent.Alignment.BASELINE);
            modelLayout.add(modelCombo, refreshModelsButton);
            modelLayout.setFlexGrow(1, modelCombo);

            dynamicFormLayout.add(urlField, apiKeyLayout, modelLayout, requestTimeoutField, maxRetriesField,
                    additionalParametersField, testConnectionButton);
            dynamicFormLayout.setColspan(urlField, 2);
            dynamicFormLayout.setColspan(apiKeyLayout, 2);
            dynamicFormLayout.setColspan(modelLayout, 2);
            dynamicFormLayout.setColspan(additionalParametersField, 2);
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            locals = {@APILocals(name = "compatible", type = OpenAICompatible.class),
                    @APILocals(name = "modelName", type = String.class)})
    private void populateFields() {
        if (ai == null) {
            return;
        }

        nameField.setValue(StringUtils.defaultString(ai.getName()));
        typeCombo.setValue(ai.getAiType());
        sharedCheckbox.setValue(ai.isShared());
        // older providers may have no limits stored - offer the defaults, saving requires a value
        maxContextField.setValue(ai.getMaxContext() != null && ai.getMaxContext() > 0 ? ai.getMaxContext() : DEFAULT_MAX_CONTEXT);
        maxResponseField.setValue(ai.getMaxCompletionTokens() != null && ai.getMaxCompletionTokens() > 0 ? ai.getMaxCompletionTokens() : DEFAULT_MAX_RESPONSE);
        jailbreakField.setValue(StringUtils.defaultString(ai.getJailbreak()));
        needsJailbreakCheckbox.setValue(Boolean.TRUE.equals(ai.getNeedsJailbreak()));
        jailbreakField.setEnabled(Boolean.TRUE.equals(ai.getNeedsJailbreak()));

        enabledReasoningCheckbox.setValue(Boolean.TRUE.equals(ai.getEnabledReasoning()));
        reasoningEffortCombo.setValue(ai.getReasoningEffort() != null ? ai.getReasoningEffort() : ReasoningEffort.NONE);
        reasoningEffortCombo.setEnabled(Boolean.TRUE.equals(ai.getEnabledReasoning()));

        if (ai.getAiType() == AIType.OPEN_AI_COMPATIBLE && ai instanceof OpenAICompatible compatible) {
            urlField.setValue(StringUtils.defaultString(compatible.getUri()));

            apiKeyField.setValue("");
            apiKeyField.setEnabled(false);
            resetApiKeyButton.setVisible(true);
            resetApiKeyButton.setEnabled(true);

            String modelName = compatible.getModelName() != null ? compatible.getModelName() : compatible.getModel();
            if (modelName != null) {
                modelCombo.setItems(List.of(modelName));
                modelCombo.setValue(modelName);
            }

            additionalParametersField.setValue(gson.toJson(compatible.getAdditionalParameters()));
            requestTimeoutField.setValue(compatible.getRequestTimeoutSeconds() != null && compatible.getRequestTimeoutSeconds() > 0
                    ? compatible.getRequestTimeoutSeconds() : OpenAICompatibleInferenceService.DEFAULT_TIMEOUT_SECONDS);
            maxRetriesField.setValue(compatible.getMaxRetries() != null && compatible.getMaxRetries() >= 0
                    ? compatible.getMaxRetries() : OpenAICompatibleInferenceService.DEFAULT_MAX_RETRIES);
        }
    }

    /**
     * Provider with the values currently in the dialog (not saved), null when they can't be used - the user was told
     * why.
     */
    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = OpenAICompatible.class),
            parameters = {@APIParameter(name = "needsModel", type = boolean.class)},
            locals = {@APILocals(name = "compatible", type = OpenAICompatible.class),
                    @APILocals(name = "c", type = OpenAICompatible.class),
                    @APILocals(name = "apiKeyToUse", type = String.class),
                    @APILocals(name = "copy", type = OpenAICompatible.class),
                    @APILocals(name = "parameters", type = JsonElement.class),
                    @APILocals(name = "e", type = Exception.class)})
    private OpenAICompatible currentProvider(boolean needsModel) {
        if (StringUtils.isBlank(urlField.getValue()) || (needsModel && StringUtils.isBlank(modelCombo.getValue()))) {
            Notification.warning(loc.getValue(L.MSG_VALIDATION_FAILED_CANT_SAVE));
            return null;
        }

        OpenAICompatible compatible = (ai instanceof OpenAICompatible c) ? c : null;

        String apiKeyToUse;
        if (apiKeyField.isEnabled()) {
            apiKeyToUse = apiKeyField.getValue();
        } else if (compatible != null) {
            apiKeyToUse = compatible.getApiKey();
        } else {
            return null;
        }

        OpenAICompatible copy = new OpenAICompatible();
        copy.setAiType(AIType.OPEN_AI_COMPATIBLE);
        copy.setUri(urlField.getValue().trim());
        copy.setApiKey(apiKeyToUse);
        copy.setModel(modelCombo.getValue());
        copy.setModelName(modelCombo.getValue());
        copy.setRequestTimeoutSeconds(requestTimeoutField.getValue());
        copy.setMaxRetries(maxRetriesField.getValue());
        if (StringUtils.isNotBlank(additionalParametersField.getValue())) {
            try {
                JsonElement parameters = JsonParser.parseString(additionalParametersField.getValue());
                if (!parameters.isJsonObject()) {
                    Notification.warning(loc.getValue(L.MSG_INVALID_JSON_OBJECT));
                    return null;
                }
                copy.setAdditionalParameters(parameters.getAsJsonObject());
            } catch (Exception e) {
                Notification.warning(loc.getValue(L.MSG_INVALID_JSON_OBJECT));
                return null;
            }
        }
        return copy;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            locals = {@APILocals(name = "copy", type = OpenAICompatible.class),
                    @APILocals(name = "models", type = List.class),
                    @APILocals(name = "e", type = Exception.class)})
    private void fetchModels() {
        if (typeCombo.getValue() != AIType.OPEN_AI_COMPATIBLE) {
            return;
        }
        OpenAICompatible copy = currentProvider(false);
        if (copy == null) {
            return;
        }

        try {
            List<String> models = new ArrayList<>(inferenceServices.forAI(copy).getModels());

            if (modelCombo.getValue() != null && !models.contains(modelCombo.getValue())) {
                models.add(0, modelCombo.getValue());
            }

            modelCombo.setItems(models);
        } catch (Exception e) {
            if (InferenceErrors.find(e) != null) {
                Notification.error(InferenceErrors.describe(loc, e), Notification.DURATION_LONGER);
            } else {
                UIUtils.showError(loc.getValue(L.MSG_FETCH_MODELS_FAILED), e);
            }
        }
    }

    /**
     * Lists the models and asks the model for one token, with the values in the dialog. Not retried, so a wrong
     * setting shows up at once.
     */
    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            locals = {@APILocals(name = "copy", type = OpenAICompatible.class),
                    @APILocals(name = "models", type = List.class),
                    @APILocals(name = "e", type = Exception.class)})
    private void testConnection() {
        if (typeCombo.getValue() != AIType.OPEN_AI_COMPATIBLE) {
            return;
        }
        OpenAICompatible copy = currentProvider(true);
        if (copy == null) {
            return;
        }
        copy.setMaxRetries(0);

        try {
            List<String> models = inferenceServices.forAI(copy).testConnection();
            Notification.success(models.isEmpty()
                    ? loc.getValue(L.MSG_CONNECTION_OK_NO_MODELS)
                    : String.format(loc.getValue(L.MSG_CONNECTION_OK), models.size()));
        } catch (Exception e) {
            if (InferenceErrors.find(e) != null) {
                Notification.error(InferenceErrors.describe(loc, e), Notification.DURATION_LONGER);
            } else {
                UIUtils.internalServerError(loc, e);
            }
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            locals = {@APILocals(name = "name", type = String.class),
                    @APILocals(name = "type", type = AIType.class),
                    @APILocals(name = "maxContext", type = Integer.class),
                    @APILocals(name = "maxResponse", type = Integer.class),
                    @APILocals(name = "additionalParams", type = String.class),
                    @APILocals(name = "jsonElement", type = JsonElement.class),
                    @APILocals(name = "e", type = Exception.class),
                    @APILocals(name = "compatible", type = OpenAICompatible.class),
                    @APILocals(name = "selectedModel", type = String.class)})
    private void save() {
        String name = nameField.getValue();
        AIType type = typeCombo.getValue();

        Integer maxContext = maxContextField.getValue();
        Integer maxResponse = maxResponseField.getValue();

        if (StringUtils.isBlank(name) || type == null || maxContext == null || maxContext <= 0
                || maxResponse == null || maxResponse <= 0) {
            Notification.warning(loc.getValue(L.MSG_VALIDATION_FAILED_CANT_SAVE));
            return;
        }

        if (maxResponse >= maxContext) {
            Notification.warning(loc.getValue(L.MSG_AI_RESPONSE_EXCEEDS_CONTEXT));
            return;
        }

        if (type == AIType.OPEN_AI_COMPATIBLE) {
            if (StringUtils.isBlank(urlField.getValue()) || StringUtils.isBlank(modelCombo.getValue())) {
                Notification.warning(loc.getValue(L.MSG_VALIDATION_FAILED_CANT_SAVE));
                return;
            }

            if (requestTimeoutField.getValue() == null || requestTimeoutField.getValue() <= 0
                    || maxRetriesField.getValue() == null || maxRetriesField.getValue() < 0) {
                Notification.warning(loc.getValue(L.MSG_VALIDATION_FAILED_CANT_SAVE));
                return;
            }

            String additionalParams = additionalParametersField.getValue();
            if (StringUtils.isNotBlank(additionalParams)) {
                try {
                    JsonElement jsonElement = JsonParser.parseString(additionalParams);
                    if (!jsonElement.isJsonObject()) {
                        Notification.warning(loc.getValue(L.MSG_INVALID_JSON_OBJECT));
                        return;
                    }
                } catch (Exception e) {
                    Notification.warning(loc.getValue(L.MSG_INVALID_JSON_OBJECT));
                    return;
                }
            }
        }

        try {
            if (ai == null) {
                switch (type) {
                    case OPEN_AI_COMPATIBLE -> ai = new OpenAICompatible();
                    default -> throw new RuntimeException();
                }
            }

            ai.setName(name.trim());
            ai.setAiType(type);
            ai.setShared(sharedCheckbox.isVisible() && sharedCheckbox.getValue());
            ai.setMaxContext(maxContext);
            ai.setMaxCompletionTokens(maxResponse);
            ai.setJailbreak(jailbreakField.getValue());
            ai.setNeedsJailbreak(needsJailbreakCheckbox.getValue());
            ai.setEnabledReasoning(enabledReasoningCheckbox.getValue());
            ai.setReasoningEffort(reasoningEffortCombo.getValue());

            if (type == AIType.OPEN_AI_COMPATIBLE && ai instanceof OpenAICompatible compatible) {
                compatible.setUri(urlField.getValue().trim());

                if (apiKeyField.isEnabled()) {
                    compatible.setApiKey(apiKeyField.getValue());
                }

                String selectedModel = modelCombo.getValue().trim();
                compatible.setModelName(selectedModel);
                compatible.setModel(selectedModel);
                compatible.setAdditionalParameters(gson.fromJson(additionalParametersField.getValue(), JsonObject.class));
                compatible.setRequestTimeoutSeconds(requestTimeoutField.getValue());
                compatible.setMaxRetries(maxRetriesField.getValue());
            }

            ai = aiService.save(ai);
            close();

            if (onSave != null) {
                onSave.run();
            }
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Runnable.class))
    public Runnable getOnSave() {
        return onSave;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "onSave", type = Runnable.class)})
    public void setOnSave(Runnable onSave) {
        this.onSave = onSave;
    }
}