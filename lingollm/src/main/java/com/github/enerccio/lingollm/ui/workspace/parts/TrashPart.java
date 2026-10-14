package com.github.enerccio.lingollm.ui.workspace.parts;

import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.security.service.UserService;
import com.github.enerccio.lingollm.domain.traits.APILocals;
import com.github.enerccio.lingollm.domain.traits.APIReturn;
import com.github.enerccio.lingollm.domain.traits.Extendable;
import com.github.enerccio.lingollm.domain.traits.PublicAPI;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.ui.widgets.TrashGrid;
import com.github.enerccio.lingollm.ui.workspace.Workspace;
import com.github.enerccio.lingollm.ui.workspace.WorkspaceComponent;
import com.github.enerccio.lingollm.utils.UIUtils;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

/**
 * Deleted objects of the user, they can be restored until an administrator runs the cleanup. Administrators see the
 * deleted objects of all users and can restore any of them.
 */
@Configurable
@Extendable
public class TrashPart implements WorkspaceComponent {

    @Autowired
    private Localization loc;

    @Autowired
    private UserService userService;

    @Autowired
    private User currentUser;

    private final Workspace workspace;
    private TrashGrid trashGrid;

    public TrashPart(Workspace workspace) {
        this.workspace = workspace;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = String.class))
    public String getName() {
        return loc.getValue(L.LABEL_TRASH);
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Component.class),
            locals = {@APILocals(name = "mainLayout", type = VerticalLayout.class),
                    @APILocals(name = "titleSpan", type = Span.class),
                    @APILocals(name = "user", type = User.class)})
    @Override
    public Component create() throws Exception {
        VerticalLayout mainLayout = new VerticalLayout();
        mainLayout.setSizeFull();
        mainLayout.setPadding(true);
        mainLayout.setSpacing(true);

        Span titleSpan = new Span(loc.getValue(L.LABEL_TRASH));
        titleSpan.addClassName(CssStyles.SECTION_TITLE);

        User user = userService.find(currentUser.getId());
        trashGrid = new TrashGrid(user != null && user.isAdmin()).create();
        // restored objects are back in their own tabs
        trashGrid.addRestoreListener(() -> {
            try {
                workspace.refresh();
            } catch (Exception e) {
                UIUtils.internalServerError(loc, e);
            }
        });

        mainLayout.add(titleSpan, trashGrid);
        mainLayout.setFlexGrow(1, trashGrid);
        return mainLayout;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class))
    @Override
    public void refresh() throws Exception {
        if (trashGrid != null) {
            trashGrid.refresh();
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
