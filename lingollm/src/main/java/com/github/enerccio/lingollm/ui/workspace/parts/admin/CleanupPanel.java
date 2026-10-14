package com.github.enerccio.lingollm.ui.workspace.parts.admin;

import com.flowingcode.vaadin.addons.fontawesome.FontAwesome.Solid;
import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.domain.service.CleanupService;
import com.github.enerccio.lingollm.domain.service.CleanupService.BlockedEntity;
import com.github.enerccio.lingollm.domain.service.CleanupService.CleanupPlan;
import com.github.enerccio.lingollm.domain.service.CleanupService.EntityStats;
import com.github.enerccio.lingollm.domain.service.CleanupService.ReferenceDescriptor;
import com.github.enerccio.lingollm.domain.traits.*;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.ui.dialogs.ConfirmDialog;
import com.github.enerccio.lingollm.ui.widgets.Notification;
import com.github.enerccio.lingollm.ui.widgets.TrashGrid;
import com.github.enerccio.lingollm.utils.UIUtils;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Configurable
@Extendable
public class CleanupPanel {

    @Autowired
    private Localization loc;

    @Autowired
    private CleanupService cleanupService;

    private Grid<EntityStats> statsGrid;
    private Grid<BlockedEntity> blockedGrid;
    private Grid<ReferenceDescriptor> modelGrid;
    private Button runButton;
    private TrashGrid trashGrid;

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Component.class),
            locals = {@APILocals(name = "layout", type = VerticalLayout.class),
                    @APILocals(name = "description", type = Span.class),
                    @APILocals(name = "toolbar", type = HorizontalLayout.class),
                    @APILocals(name = "analyzeButton", type = Button.class),
                    @APILocals(name = "blockedTitle", type = Span.class),
                    @APILocals(name = "modelDetails", type = Details.class),
                    @APILocals(name = "trashDetails", type = Details.class)})
    public Component create() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();

        Span description = new Span(loc.getValue(L.MSG_CLEANUP_DESCRIPTION));
        description.addClassName(CssStyles.TEXT_SECONDARY);

        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setWidthFull();
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);

        Button analyzeButton = new Button(loc.getValue(L.LABEL_ANALYZE), Solid.SEARCH.create(), e -> analyze());

        runButton = new Button(loc.getValue(L.LABEL_RUN_CLEANUP), Solid.BROOM.create(), e -> runCleanup());
        runButton.setThemeName("primary error");
        runButton.setEnabled(false);

        toolbar.add(analyzeButton, runButton);

        statsGrid = new Grid<>();
        statsGrid.setAllRowsVisible(true);
        statsGrid.addColumn(stats -> stats.getType().getSimpleName())
                .setHeader(loc.getValue(L.LABEL_ENTITY))
                .setFlexGrow(1);
        statsGrid.addColumn(EntityStats::getSoftDeleted)
                .setHeader(loc.getValue(L.LABEL_SOFT_DELETED))
                .setFlexGrow(0)
                .setWidth("140px");
        statsGrid.addColumn(EntityStats::getPurgeable)
                .setHeader(loc.getValue(L.LABEL_PURGEABLE))
                .setFlexGrow(0)
                .setWidth("140px");
        statsGrid.addColumn(EntityStats::getBlocked)
                .setHeader(loc.getValue(L.LABEL_BLOCKED))
                .setFlexGrow(0)
                .setWidth("140px");

        Span blockedTitle = new Span(loc.getValue(L.LABEL_BLOCKED_ENTITIES));
        blockedTitle.addClassName(CssStyles.TEXT_BOLD);

        blockedGrid = new Grid<>();
        blockedGrid.setSizeFull();
        blockedGrid.setMinHeight("200px");
        blockedGrid.addColumn(blocked -> blocked.getKey().type().getSimpleName())
                .setHeader(loc.getValue(L.LABEL_ENTITY))
                .setFlexGrow(0)
                .setWidth("160px");
        blockedGrid.addColumn(BlockedEntity::getLabel)
                .setHeader(loc.getValue(L.LABEL_NAME))
                .setFlexGrow(1);
        blockedGrid.addColumn(blocked -> String.join(", ", blocked.getReasons()))
                .setHeader(loc.getValue(L.LABEL_REFERENCED_BY))
                .setFlexGrow(2);

        modelGrid = new Grid<>();
        modelGrid.setAllRowsVisible(true);
        modelGrid.addColumn(ReferenceDescriptor::getReferrerEntity)
                .setHeader(loc.getValue(L.LABEL_REFERRER))
                .setFlexGrow(1);
        modelGrid.addColumn(ReferenceDescriptor::getField)
                .setHeader(loc.getValue(L.LABEL_FIELD))
                .setFlexGrow(1);
        modelGrid.addColumn(descriptor -> descriptor.getTargetType() != null ? descriptor.getTargetType().getSimpleName() : "*")
                .setHeader(loc.getValue(L.LABEL_TARGET))
                .setFlexGrow(1);
        modelGrid.addColumn(descriptor -> loc.getValue(loc.getCleanupPolicy(descriptor.getPolicy())))
                .setHeader(loc.getValue(L.LABEL_POLICY))
                .setFlexGrow(1);

        Details modelDetails = new Details(loc.getValue(L.LABEL_REFERENCE_MODEL), modelGrid);
        modelDetails.setWidthFull();
        modelDetails.addOpenedChangeListener(e -> {
            if (e.isOpened()) {
                refreshModel();
            }
        });

        // what a purge removes, and the way to get it back before that
        Details trashDetails = new Details(loc.getValue(L.LABEL_TRASH));
        trashDetails.setWidthFull();
        trashDetails.addOpenedChangeListener(e -> {
            if (!e.isOpened()) {
                return;
            }
            try {
                if (trashGrid == null) {
                    trashGrid = new TrashGrid(true).create();
                    trashGrid.setHeight("500px");
                    trashDetails.add(trashGrid);
                } else {
                    trashGrid.refresh();
                }
            } catch (Exception ex) {
                UIUtils.internalServerError(loc, ex);
            }
        });

        layout.add(description, toolbar, statsGrid, blockedTitle, blockedGrid, trashDetails, modelDetails);
        layout.setFlexGrow(1, blockedGrid);
        return layout;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            locals = {@APILocals(name = "e", type = Exception.class)})
    private void analyze() {
        try {
            showPlan(cleanupService.analyze());
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            locals = {@APILocals(name = "plan", type = CleanupService.CleanupPlan.class),
                    @APILocals(name = "e", type = Exception.class)})
    private void runCleanup() {
        try {
            CleanupPlan plan = cleanupService.analyze();
            showPlan(plan);
            if (plan.getPurge().isEmpty()) {
                Notification.warning(loc.getValue(L.MSG_NOTHING_TO_CLEANUP));
                return;
            }
            ConfirmDialog.show(String.format(loc.getValue(L.MSG_CONFIRM_CLEANUP), plan.getPurge().size()), () -> {
                try {
                    CleanupPlan result = cleanupService.purge();
                    Notification.success(String.format(loc.getValue(L.MSG_CLEANUP_DONE), result.getPurge().size()));
                    showPlan(cleanupService.analyze());
                    if (trashGrid != null) {
                        trashGrid.refresh();
                    }
                } catch (Exception e) {
                    UIUtils.internalServerError(loc, e);
                }
            });
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "plan", type = CleanupService.CleanupPlan.class)},
            locals = {@APILocals(name = "stats", type = List.class)})
    private void showPlan(CleanupPlan plan) {
        List<EntityStats> stats = new ArrayList<>(plan.getStats().values());
        stats.sort(Comparator.comparing(s -> s.getType().getSimpleName()));
        statsGrid.setItems(stats);
        blockedGrid.setItems(new ArrayList<>(plan.getBlocked().values()));
        runButton.setEnabled(!plan.getPurge().isEmpty());
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            locals = {@APILocals(name = "descriptors", type = List.class),
                    @APILocals(name = "e", type = Exception.class)})
    private void refreshModel() {
        try {
            List<ReferenceDescriptor> descriptors = new ArrayList<>(cleanupService.getReferenceModel());
            descriptors.sort(Comparator.comparing(ReferenceDescriptor::getReferrerEntity).thenComparing(ReferenceDescriptor::getField));
            modelGrid.setItems(descriptors);
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class))
    public void refresh() {
        // analysis is expensive, it runs only on demand
    }
}
