package com.github.enerccio.lingollm.ui.workspace.parts;

import com.flowingcode.vaadin.addons.fontawesome.FontAwesome.Solid;
import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.UIConstants;
import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.model.impl.Usage;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.service.AIService;
import com.github.enerccio.lingollm.domain.service.UsageService;
import com.github.enerccio.lingollm.domain.traits.APILocals;
import com.github.enerccio.lingollm.domain.traits.APIReturn;
import com.github.enerccio.lingollm.domain.traits.Extendable;
import com.github.enerccio.lingollm.domain.traits.PublicAPI;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.ui.dialogs.*;
import com.github.enerccio.lingollm.ui.workspace.Workspace;
import com.github.enerccio.lingollm.ui.workspace.WorkspaceComponent;
import com.github.enerccio.lingollm.utils.UIUtils;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.text.NumberFormat;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configurable
@Extendable
public class AIPart implements WorkspaceComponent {

    @Autowired
    private Localization loc;

    @Autowired
    private AIService aiService;

    @Autowired
    private UsageService usageService;

    @Autowired
    private User currentUser;

    private final Workspace workspace;
    private Grid<AI> grid;
    private Button addModelButton;
    private final Map<Long, Usage> usages = new HashMap<>();
    private boolean administrator;
    private VerticalLayout mainLayout;

    public AIPart(Workspace workspace) {
        this.workspace = workspace;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = String.class))
    public String getName() {
        return loc.getValue(L.LABEL_MODELS);
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Component.class),
            locals = {@APILocals(name = "headerLayout", type = HorizontalLayout.class),
                    @APILocals(name = "titleSpan", type = Span.class),
                    @APILocals(name = "addModelButton", type = Button.class),
                    @APILocals(name = "usagePerUserButton", type = Button.class)})
    @Override
    public Component create() throws Exception {
        mainLayout = new VerticalLayout();
        mainLayout.setSizeFull();
        mainLayout.setPadding(true);
        mainLayout.setSpacing(true);

        HorizontalLayout headerLayout = new HorizontalLayout();
        headerLayout.setWidthFull();
        headerLayout.setAlignItems(FlexComponent.Alignment.CENTER);

        Span titleSpan = new Span(loc.getValue(L.LABEL_MODELS));
        titleSpan.addClassName(CssStyles.SECTION_TITLE);

        Button addModelButton = new Button(loc.getValue(L.LABEL_ADD_MODEL), event -> {
            AIDialog dialog = new AIDialog();
            dialog.setOnSave(this::refreshGrid);
            dialog.create();
            dialog.open();
        });
        addModelButton.setThemeName("primary");
        this.addModelButton = addModelButton;

        // usage of everybody, only for administrators
        Button usagePerUserButton = new Button(loc.getValue(L.LABEL_USAGE_PER_USER), Solid.CHART_BAR.create(),
                event -> UsageDialog.show());
        usagePerUserButton.setVisible(aiService.isAdministrator());

        headerLayout.add(titleSpan, usagePerUserButton, addModelButton);
        headerLayout.setFlexGrow(1, titleSpan);
        headerLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        grid = new Grid<>(AI.class, false);
        grid.setSizeFull();

        grid.addColumn(ai -> loc.getValue(loc.getAIType(ai.getAiType())))
                .setHeader(loc.getValue(L.LABEL_TYPE))
                .setFlexGrow(1);

        grid.addColumn(AI::getName)
                .setHeader(loc.getValue(L.LABEL_NAME))
                .setFlexGrow(1);

        grid.addColumn(ai -> loc.getValue(ai.isShared() ? L.LABEL_YES : L.LABEL_NO))
                .setHeader(loc.getValue(L.LABEL_SHARED))
                .setFlexGrow(0)
                .setWidth(UIConstants.TOOL_COLUMN_SIZE_LARGE);

        grid.addColumn(ai -> formatTokens(usages.get(ai.getId()) == null ? 0 : usages.get(ai.getId()).getPrefillTokens()))
                .setHeader(loc.getValue(L.LABEL_TOKENS_SENT))
                .setFlexGrow(0)
                .setWidth(UIConstants.NUMBER_COLUMN_SIZE);

        grid.addColumn(ai -> formatTokens(usages.get(ai.getId()) == null ? 0 : usages.get(ai.getId()).getGeneratedTokens()))
                .setHeader(loc.getValue(L.LABEL_TOKENS_GENERATED))
                .setFlexGrow(0)
                .setWidth(UIConstants.NUMBER_COLUMN_SIZE);

        grid.addComponentColumn(ai -> {
            HorizontalLayout actions = new HorizontalLayout();
            actions.setSpacing(true);

            Button editButton = new Button(Solid.PEN.create(), event -> {
                AIDialog dialog = new AIDialog(ai);
                dialog.setOnSave(this::refreshGrid);
                dialog.create();
                dialog.open();
            });

            Button deleteButton = new Button(Solid.TRASH.create(), event ->
                    ConfirmDialog.show(loc.getValue(L.MSG_CONFIRM_DELETE), () -> {
                        try {
                            aiService.delete(ai, false);
                            refreshGrid();
                        } catch (Exception e) {
                            UIUtils.internalServerError(loc, e);
                        }
                    }));
            deleteButton.setThemeName("error tertiary");

            // what the user used of a shared AI and what they are limited to
            Button infoButton = new Button(Solid.INFO_CIRCLE.create(), event -> AILimitInfoDialog.show(ai));
            infoButton.setTooltipText(loc.getValue(L.LABEL_YOUR_LIMITS));
            infoButton.setVisible(ai.isShared());

            // the limits of the users of a shared AI
            Button limitsButton = new Button(Solid.USER_LOCK.create(), event -> AILimitsDialog.show(ai));
            limitsButton.setTooltipText(loc.getValue(L.LABEL_LIMITS));

            // AIs shared by somebody else can be used, not seen or changed
            boolean own = aiService.isOwnedByCurrentUser(ai);
            editButton.setVisible(own);
            deleteButton.setVisible(own);
            limitsButton.setVisible(own && ai.isShared() && administrator);

            actions.add(infoButton, limitsButton, editButton, deleteButton);
            return actions;
        }).setHeader("").setFlexGrow(0).setWidth(UIConstants.TOOL_COLUMN_SIZE_GIANT);

        mainLayout.add(headerLayout, grid);
        mainLayout.setFlexGrow(1, grid);

        refreshGrid();

        return mainLayout;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class))
    @Override
    public void refresh() throws Exception {
        refreshGrid();
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            locals = {@APILocals(name = "connections", type = List.class),
                    @APILocals(name = "e", type = Exception.class)})
    private void refreshGrid() {
        if (grid == null) {
            return;
        }
        try {
            List<AI> connections = aiService.findAllForUser();
            connections.sort(Comparator.comparing(AI::getName));
            administrator = aiService.isAdministrator();
            usages.clear();
            usageService.findAllForUser().forEach(usage -> usages.put(usage.getAi().getId(), usage));
            addModelButton.setVisible(aiService.canCreate());
            grid.setItems(connections);
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

    private static String formatTokens(long tokens) {
        return NumberFormat.getIntegerInstance().format(tokens);
    }
}
