package com.github.enerccio.lingollm.ui.workspace.phone.parts;

import com.flowingcode.vaadin.addons.fontawesome.FontAwesome.Solid;
import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.model.impl.Usage;
import com.github.enerccio.lingollm.domain.service.AIService;
import com.github.enerccio.lingollm.domain.service.UsageService;
import com.github.enerccio.lingollm.domain.traits.*;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.ui.dialogs.AIDialog;
import com.github.enerccio.lingollm.ui.dialogs.AILimitInfoDialog;
import com.github.enerccio.lingollm.ui.dialogs.AILimitsDialog;
import com.github.enerccio.lingollm.ui.dialogs.ConfirmDialog;
import com.github.enerccio.lingollm.ui.workspace.Workspace;
import com.github.enerccio.lingollm.ui.workspace.WorkspaceComponent;
import com.github.enerccio.lingollm.utils.UIUtils;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
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

/**
 * The model list for phones. Instead of the type, name and action columns of the desktop list it has a text column
 * (type and name on two lines) and a column with the buttons, so it fits a narrow screen.
 */
@Configurable
@Extendable
public class PhoneAIPart implements WorkspaceComponent {

    @Autowired
    private Localization loc;

    @Autowired
    private AIService aiService;

    @Autowired
    private UsageService usageService;

    private final Workspace workspace;
    private Grid<AI> grid;
    private Button addModelButton;
    private final Map<Long, Usage> usages = new HashMap<>();
    private boolean administrator;

    public PhoneAIPart(Workspace workspace) {
        this.workspace = workspace;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = String.class))
    public String getName() {
        return loc.getValue(L.LABEL_MODELS);
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Component.class),
            locals = {@APILocals(name = "mainLayout", type = VerticalLayout.class),
                    @APILocals(name = "addModelButton", type = Button.class)})
    @Override
    public Component create() throws Exception {
        VerticalLayout mainLayout = new VerticalLayout();
        mainLayout.setSizeFull();
        mainLayout.setPadding(true);
        mainLayout.setSpacing(true);

        Button addModelButton = new Button(loc.getValue(L.LABEL_ADD_MODEL), Solid.PLUS.create(), event -> {
            AIDialog dialog = new AIDialog();
            dialog.setOnSave(this::refreshGrid);
            dialog.create();
            dialog.open();
        });
        addModelButton.setThemeName("primary");
        addModelButton.setWidthFull();
        this.addModelButton = addModelButton;

        grid = new Grid<>();
        grid.setSizeFull();
        // rows grow with their content, so a long name breaks into lines instead of running under the buttons
        grid.addThemeVariants(GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.addComponentColumn(this::createText).setFlexGrow(1);
        grid.addComponentColumn(this::createActions).setFlexGrow(0).setAutoWidth(true);

        mainLayout.add(addModelButton, grid);
        mainLayout.setFlexGrow(1, grid);

        refreshGrid();
        return mainLayout;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Component.class),
            parameters = {@APIParameter(name = "ai", type = AI.class)},
            locals = {@APILocals(name = "textLayout", type = VerticalLayout.class)})
    private Component createText(AI ai) {
        VerticalLayout textLayout = new VerticalLayout();
        textLayout.setPadding(false);
        textLayout.setSpacing(false);
        textLayout.setWidthFull();
        textLayout.addClassName(CssStyles.MIN_WIDTH_0);

        textLayout.add(createLine(loc.getValue(L.LABEL_TYPE), loc.getValue(loc.getAIType(ai.getAiType()))));
        textLayout.add(createLine(loc.getValue(L.LABEL_NAME), ai.getName()));
        if (ai.isShared()) {
            textLayout.add(createLine(loc.getValue(L.LABEL_SHARED), loc.getValue(L.LABEL_YES)));
        }
        textLayout.add(createLine(loc.getValue(L.LABEL_TOKENS_SENT), formatTokens(usages.get(ai.getId()) == null ? 0 : usages.get(ai.getId()).getPrefillTokens())));
        textLayout.add(createLine(loc.getValue(L.LABEL_TOKENS_GENERATED), formatTokens(usages.get(ai.getId()) == null ? 0 : usages.get(ai.getId()).getGeneratedTokens())));
        return textLayout;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Span.class),
            parameters = {@APIParameter(name = "label", type = String.class),
                    @APIParameter(name = "value", type = String.class)},
            locals = {@APILocals(name = "line", type = Span.class)})
    private Span createLine(String label, String value) {
        Span line = new Span(label + ": " + value);
        line.addClassNames(CssStyles.WHITE_SPACE_NORMAL, CssStyles.WRAP_ANYWHERE);
        return line;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Component.class),
            parameters = {@APIParameter(name = "ai", type = AI.class)},
            locals = {@APILocals(name = "editButton", type = Button.class),
                    @APILocals(name = "deleteButton", type = Button.class),
                    @APILocals(name = "infoButton", type = Button.class),
                    @APILocals(name = "limitsButton", type = Button.class),
                    @APILocals(name = "actions", type = HorizontalLayout.class)})
    private Component createActions(AI ai) {
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

        HorizontalLayout actions = new HorizontalLayout(infoButton, limitsButton, editButton, deleteButton);
        actions.setSpacing(false);
        actions.setAlignItems(FlexComponent.Alignment.CENTER);
        actions.addClassName(CssStyles.FLEX_SHRINK_0);
        return actions;
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
