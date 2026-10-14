package com.github.enerccio.lingollm.ui.dialogs;

import com.flowingcode.vaadin.addons.fontawesome.FontAwesome.Solid;
import com.github.enerccio.lingollm.UIConstants;
import com.github.enerccio.lingollm.domain.collections.LimitStrategy;
import com.github.enerccio.lingollm.domain.components.AILimit;
import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.service.AILimitService;
import com.github.enerccio.lingollm.domain.service.AILimitService.Entry;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.utils.UIUtils;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;

/**
 * The limits an administrator set for the users of a shared AI. Users that are not in the table are not limited.
 */
@Configurable
public class AILimitsDialog extends Dialog {

    @Autowired
    private AILimitService limitService;

    @Autowired
    private Localization loc;

    private final AI ai;
    private final Grid<Entry> grid = new Grid<>(Entry.class, false);

    public static void show(AI ai) {
        new AILimitsDialog(ai).open();
    }

    private AILimitsDialog(AI ai) {
        this.ai = ai;
        setWidth("1100px");
        grid.setHeight("400px");
        add(grid);

        Button close = new Button("", e -> close());
        Button addButton = new Button("", Solid.PLUS.create(), e -> editLimit(null));
        addButton.setThemeName("primary");
        // texts need the injected localization, which is there only after the constructor
        addAttachListener(e -> {
            setHeaderTitle(loc.getValue(L.LABEL_LIMITS) + ": " + ai.getName());
            close.setText(loc.getValue(L.LABEL_CLOSE));
            addButton.setText(loc.getValue(L.LABEL_ADD_LIMIT));
            createColumns();
            load();
        });
        getFooter().add(close, addButton);
    }

    private void createColumns() {
        grid.removeAllColumns();
        grid.addColumn(entry -> entry.user().getLogin())
                .setHeader(loc.getValue(L.LABEL_USER))
                .setFlexGrow(1);
        grid.addColumn(entry -> loc.getValue(loc.getLimitStrategy(entry.limit().getStrategy())))
                .setHeader(loc.getValue(L.LABEL_LIMIT_TYPE))
                .setFlexGrow(1);
        grid.addColumn(entry -> isTokenLimit(entry) ? loc.getValue(loc.getResetStrategy(entry.limit().getResetStrategy())) : "")
                .setHeader(loc.getValue(L.LABEL_RESET_PERIOD))
                .setFlexGrow(1);
        grid.addColumn(entry -> isTokenLimit(entry) ? format(entry.limit().getPrefillTokens()) : "")
                .setHeader(loc.getValue(L.LABEL_TOKENS_SENT))
                .setFlexGrow(0).setWidth(UIConstants.NUMBER_COLUMN_SIZE);
        grid.addColumn(entry -> isTokenLimit(entry) ? format(entry.limit().getGeneratedTokens())
                        + (entry.limit().getGeneratedTokens() != null && entry.limit().isHardGeneratedLimit()
                        ? " (" + loc.getValue(L.LABEL_LIMIT_HARD) + ")" : "") : "")
                .setHeader(loc.getValue(L.LABEL_TOKENS_GENERATED))
                .setFlexGrow(0).setWidth(UIConstants.NUMBER_COLUMN_SIZE);
        grid.addColumn(entry -> isTokenLimit(entry) ? format(entry.limit().getTotalTokens()) : "")
                .setHeader(loc.getValue(L.LABEL_TOKENS_TOTAL))
                .setFlexGrow(0).setWidth(UIConstants.NUMBER_COLUMN_SIZE);
        grid.addComponentColumn(entry -> {
            Button editButton = new Button(Solid.PEN.create(), e -> editLimit(entry));
            Button deleteButton = new Button(Solid.TRASH.create(), e -> ConfirmDialog.show(loc.getValue(L.MSG_CONFIRM_DELETE), () -> {
                try {
                    limitService.removeLimit(ai, entry.user());
                    load();
                } catch (Exception ex) {
                    UIUtils.internalServerError(loc, ex);
                }
            }));
            deleteButton.setThemeName("error tertiary");
            return new HorizontalLayout(editButton, deleteButton);
        }).setHeader("").setFlexGrow(0).setWidth(UIConstants.TOOL_COLUMN_SIZE_HUGE);
    }

    private void editLimit(Entry entry) {
        AILimitEditDialog dialog = new AILimitEditDialog(ai, entry);
        dialog.setOnSave(this::load);
        dialog.open();
    }

    private void load() {
        try {
            List<Entry> entries = limitService.findLimits(ai);
            entries.sort(Comparator.comparing((Entry e) -> e.user().getLogin(), String.CASE_INSENSITIVE_ORDER));
            grid.setItems(entries);
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
    }

    private static boolean isTokenLimit(Entry entry) {
        AILimit limit = entry.limit();
        return limit.getStrategy() == LimitStrategy.TOKEN_LIMIT;
    }

    private String format(Long limit) {
        return limit == null ? loc.getValue(L.LABEL_UNLIMITED) : NumberFormat.getIntegerInstance().format(limit);
    }
}
