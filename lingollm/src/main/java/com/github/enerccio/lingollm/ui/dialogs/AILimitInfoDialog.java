package com.github.enerccio.lingollm.ui.dialogs;

import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.UIConstants;
import com.github.enerccio.lingollm.domain.collections.LimitStrategy;
import com.github.enerccio.lingollm.domain.components.AILimit;
import com.github.enerccio.lingollm.domain.components.LimitStatus;
import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.service.AILimitService;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.utils.UIUtils;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.List;

/**
 * What the current user used of a shared AI and what the administrator limited them to.
 */
@Configurable
public class AILimitInfoDialog extends Dialog {

    private record Row(String name, long used, Long limit, boolean hard) {}

    @Autowired
    private AILimitService limitService;

    @Autowired
    private Localization loc;

    private final AI ai;

    public static void show(AI ai) {
        new AILimitInfoDialog(ai).open();
    }

    private AILimitInfoDialog(AI ai) {
        this.ai = ai;
        setWidth("560px");

        Button close = new Button("", e -> close());
        // texts need the injected localization, which is there only after the constructor
        addAttachListener(e -> {
            setHeaderTitle(loc.getValue(L.LABEL_YOUR_LIMITS) + ": " + ai.getName());
            close.setText(loc.getValue(L.LABEL_CLOSE));
            load();
        });
        getFooter().add(close);
    }

    private void load() {
        removeAll();
        try {
            LimitStatus status = limitService.getStatus(ai);
            AILimit limit = status.limit();
            add(new Div(new Span(loc.getValue(loc.getLimitStrategy(limit.getStrategy())))));
            if (limit.getStrategy() != LimitStrategy.TOKEN_LIMIT) {
                return;
            }

            Grid<Row> grid = new Grid<>(Row.class, false);
            grid.setAllRowsVisible(true);
            grid.addColumn(Row::name).setHeader("").setFlexGrow(1);
            grid.addColumn(row -> format(row.used())).setHeader(loc.getValue(L.LABEL_USED)).setFlexGrow(0).setWidth(UIConstants.NUMBER_COLUMN_SIZE);
            grid.addColumn(row -> row.limit() == null ? loc.getValue(L.LABEL_UNLIMITED)
                            : format(row.limit()) + (row.hard() ? " (" + loc.getValue(L.LABEL_LIMIT_HARD) + ")" : ""))
                    .setHeader(loc.getValue(L.LABEL_LIMIT)).setFlexGrow(0).setWidth(UIConstants.NUMBER_COLUMN_SIZE);
            grid.setItems(List.of(
                    new Row(loc.getValue(L.LABEL_TOKENS_SENT), status.usedPrefill(), limit.getPrefillTokens(), false),
                    new Row(loc.getValue(L.LABEL_TOKENS_GENERATED), status.usedGenerated(), limit.getGeneratedTokens(), limit.isHardGeneratedLimit()),
                    new Row(loc.getValue(L.LABEL_TOKENS_TOTAL), status.usedTotal(), limit.getTotalTokens(), false)));
            add(grid);

            Span reset = new Span(loc.getValue(L.LABEL_RESET_PERIOD) + ": "
                    + loc.getValue(loc.getResetStrategy(limit.getResetStrategy())) + ", "
                    + loc.getValue(L.LABEL_NEXT_RESET) + ": "
                    + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(status.resetsAt()));
            reset.addClassName(CssStyles.TEXT_SECONDARY);
            add(reset);
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
    }

    private static String format(long number) {
        return NumberFormat.getIntegerInstance().format(number);
    }
}
