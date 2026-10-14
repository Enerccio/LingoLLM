package com.github.enerccio.lingollm.ui.dialogs;

import com.github.enerccio.lingollm.domain.collections.LimitStrategy;
import com.github.enerccio.lingollm.domain.collections.ResetStrategy;
import com.github.enerccio.lingollm.domain.components.AILimit;
import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.security.service.UserService;
import com.github.enerccio.lingollm.domain.service.AILimitService;
import com.github.enerccio.lingollm.domain.service.AILimitService.Entry;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.ui.widgets.Notification;
import com.github.enerccio.lingollm.utils.UIUtils;
import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.NumberField;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adds the limit of a user of a shared AI or changes one. The user of an existing limit can not be changed.
 */
@Configurable
public class AILimitEditDialog extends Dialog {

    @Autowired
    private AILimitService limitService;

    @Autowired
    private UserService userService;

    @Autowired
    private Localization loc;

    private final AI ai;
    private final Entry entry;
    private Runnable onSave;

    private final ComboBox<User> userCombo = new ComboBox<>();
    private final ComboBox<LimitStrategy> strategyCombo = new ComboBox<>();
    private final ComboBox<ResetStrategy> resetCombo = new ComboBox<>();
    private final NumberField prefillField = tokenField();
    private final NumberField generatedField = tokenField();
    private final NumberField totalField = tokenField();
    private final Checkbox hardGeneratedCheckbox = new Checkbox();
    private final Span help = new Span();

    /**
     * @param entry the limit to change, null to add one
     */
    AILimitEditDialog(AI ai, Entry entry) {
        this.ai = ai;
        this.entry = entry;
        setWidth("520px");
        setModality(ModalityMode.STRICT);
        setCloseOnEsc(false);
        setCloseOnOutsideClick(false);

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        form.add(userCombo, strategyCombo, resetCombo, prefillField, generatedField, hardGeneratedCheckbox, totalField, help);
        add(form);

        Button cancel = new Button("", e -> close());
        Button save = new Button("", e -> save());
        save.setThemeName("primary");
        getFooter().add(cancel, save);

        // texts need the injected localization, which is there only after the constructor
        addAttachListener(e -> {
            setHeaderTitle(loc.getValue(entry == null ? L.LABEL_ADD_LIMIT : L.LABEL_EDIT_LIMIT));
            cancel.setText(loc.getValue(L.LABEL_CANCEL));
            save.setText(loc.getValue(L.LABEL_SAVE));
            createFields();
        });
    }

    void setOnSave(Runnable onSave) {
        this.onSave = onSave;
    }

    private static NumberField tokenField() {
        NumberField field = new NumberField();
        field.setMin(0);
        field.setStep(1);
        field.setWidthFull();
        return field;
    }

    private void createFields() {
        userCombo.setLabel(loc.getValue(L.LABEL_USER));
        userCombo.setItemLabelGenerator(User::getLogin);
        try {
            Set<Long> limited = limitService.findLimits(ai).stream().map(e -> e.user().getId()).collect(Collectors.toSet());
            List<User> users = userService.findAll().stream()
                    .filter(u -> !u.isDeleted() && (entry != null || !limited.contains(u.getId())))
                    .sorted(Comparator.comparing(User::getLogin, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            userCombo.setItems(users);
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }

        strategyCombo.setLabel(loc.getValue(L.LABEL_LIMIT_TYPE));
        strategyCombo.setItems(LimitStrategy.values());
        strategyCombo.setItemLabelGenerator(s -> loc.getValue(loc.getLimitStrategy(s)));
        strategyCombo.setAllowCustomValue(false);
        strategyCombo.addValueChangeListener(e -> updateVisibility());

        resetCombo.setLabel(loc.getValue(L.LABEL_RESET_PERIOD));
        resetCombo.setItems(ResetStrategy.values());
        resetCombo.setItemLabelGenerator(s -> loc.getValue(loc.getResetStrategy(s)));
        resetCombo.setAllowCustomValue(false);

        prefillField.setLabel(loc.getValue(L.LABEL_LIMIT_TOKENS_SENT));
        generatedField.setLabel(loc.getValue(L.LABEL_LIMIT_TOKENS_GENERATED));
        totalField.setLabel(loc.getValue(L.LABEL_LIMIT_TOKENS_TOTAL));
        hardGeneratedCheckbox.setLabel(loc.getValue(L.LABEL_LIMIT_GENERATED_HARD));
        hardGeneratedCheckbox.setTooltipText(loc.getValue(L.HELP_LIMIT_GENERATED_HARD));
        help.setText(loc.getValue(L.HELP_LIMIT_EMPTY_UNLIMITED) + " " + loc.getValue(L.HELP_LIMIT_GENERATED_HARD));

        AILimit limit = entry == null ? AILimit.unlimited() : entry.limit();
        if (entry != null) {
            userCombo.setValue(userCombo.getListDataView().getItems()
                    .filter(u -> u.getId().equals(entry.user().getId())).findFirst().orElse(null));
            userCombo.setReadOnly(true);
        }
        // a new limit most likely is a token limit
        strategyCombo.setValue(entry == null ? LimitStrategy.TOKEN_LIMIT : limit.getStrategy());
        resetCombo.setValue(limit.getResetStrategy());
        prefillField.setValue(toDouble(limit.getPrefillTokens()));
        generatedField.setValue(toDouble(limit.getGeneratedTokens()));
        totalField.setValue(toDouble(limit.getTotalTokens()));
        hardGeneratedCheckbox.setValue(limit.isHardGeneratedLimit());
        updateVisibility();
    }

    private void updateVisibility() {
        boolean tokens = strategyCombo.getValue() == LimitStrategy.TOKEN_LIMIT;
        resetCombo.setVisible(tokens);
        prefillField.setVisible(tokens);
        generatedField.setVisible(tokens);
        hardGeneratedCheckbox.setVisible(tokens);
        totalField.setVisible(tokens);
        help.setVisible(tokens);
    }

    private void save() {
        User user = userCombo.getValue();
        if (user == null) {
            Notification.warning(loc.getValue(L.ERROR_LIMIT_USER_REQUIRED));
            return;
        }
        AILimit limit = new AILimit();
        limit.setStrategy(strategyCombo.getValue());
        limit.setResetStrategy(resetCombo.getValue());
        limit.setPrefillTokens(toLong(prefillField.getValue()));
        limit.setGeneratedTokens(toLong(generatedField.getValue()));
        limit.setTotalTokens(toLong(totalField.getValue()));
        limit.setHardGeneratedLimit(hardGeneratedCheckbox.getValue());

        AILimit.Problem problem = limit.validate();
        if (problem != null) {
            Notification.warning(loc.getValue(problem == AILimit.Problem.NEGATIVE
                    ? L.ERROR_LIMIT_NEGATIVE : L.ERROR_LIMIT_TOTAL_UNREACHABLE));
            return;
        }
        try {
            limitService.setLimit(ai, user, limit);
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
            return;
        }
        close();
        if (onSave != null) {
            onSave.run();
        }
    }

    private static Double toDouble(Long value) {
        return value == null ? null : value.doubleValue();
    }

    private static Long toLong(Double value) {
        return value == null ? null : Math.round(value);
    }
}
