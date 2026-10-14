package com.github.enerccio.lingollm.ui.dialogs;

import com.github.enerccio.lingollm.UIConstants;
import com.github.enerccio.lingollm.domain.model.impl.Usage;
import com.github.enerccio.lingollm.domain.service.UsageService;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.utils.UIUtils;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;

/**
 * What every user used of every AI, for administrators.
 */
@Configurable
public class UsageDialog extends Dialog {

    @Autowired
    private UsageService usageService;

    @Autowired
    private Localization loc;

    private final Grid<Usage> grid = new Grid<>(Usage.class, false);

    public static void show() {
        new UsageDialog().open();
    }

    private UsageDialog() {
        setWidth("1000px");
        grid.setHeight("500px");
        add(grid);

        Button close = new Button("", e -> close());
        // texts need the injected localization, which is there only after the constructor
        addAttachListener(e -> {
            setHeaderTitle(loc.getValue(L.LABEL_USAGE_PER_USER));
            close.setText(loc.getValue(L.LABEL_CLOSE));
            createColumns();
            load();
        });
        getFooter().add(close);
    }

    private void createColumns() {
        grid.removeAllColumns();
        grid.addColumn(usage -> usage.getOwner().getLogin())
                .setHeader(loc.getValue(L.LABEL_USER))
                .setFlexGrow(1);
        grid.addColumn(usage -> usage.getAi().getName())
                .setHeader(loc.getValue(L.LABEL_MODEL))
                .setFlexGrow(1);
        grid.addColumn(usage -> format(usage.getPrefillTokens()))
                .setHeader(loc.getValue(L.LABEL_TOKENS_SENT))
                .setFlexGrow(0).setWidth(UIConstants.NUMBER_COLUMN_SIZE);
        grid.addColumn(usage -> format(usage.getGeneratedTokens()))
                .setHeader(loc.getValue(L.LABEL_TOKENS_GENERATED))
                .setFlexGrow(0).setWidth(UIConstants.NUMBER_COLUMN_SIZE);
        grid.addColumn(usage -> format(usage.getRequests()))
                .setHeader(loc.getValue(L.LABEL_REQUESTS))
                .setFlexGrow(0).setWidth(UIConstants.TOOL_COLUMN_SIZE_HUGE);
        grid.addColumn(usage -> usage.getLastUsed() == null ? ""
                        : DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(usage.getLastUsed()))
                .setHeader(loc.getValue(L.LABEL_LAST_USED))
                .setFlexGrow(0).setWidth(UIConstants.DATE_COLUMN_SIZE);
    }

    private void load() {
        try {
            List<Usage> usages = usageService.findAllUsages();
            usages.sort(Comparator.comparing((Usage u) -> u.getOwner().getLogin(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(u -> u.getAi().getName(), String.CASE_INSENSITIVE_ORDER));
            grid.setItems(usages);
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
    }

    private static String format(long number) {
        return NumberFormat.getIntegerInstance().format(number);
    }
}
