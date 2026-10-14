package com.github.enerccio.lingollm.ui.workspace.phone;

import com.flowingcode.vaadin.addons.fontawesome.FontAwesome.Solid;
import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.security.service.UserService;
import com.github.enerccio.lingollm.domain.traits.*;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.ui.dialogs.UserDialog;
import com.github.enerccio.lingollm.ui.widgets.LanguageSelector;
import com.github.enerccio.lingollm.ui.workspace.DesktopWorkspace;
import com.github.enerccio.lingollm.ui.workspace.Workspace;
import com.github.enerccio.lingollm.ui.workspace.WorkspaceComponent;
import com.github.enerccio.lingollm.ui.workspace.parts.LanguagePart;
import com.github.enerccio.lingollm.ui.workspace.parts.ResourcesPart;
import com.github.enerccio.lingollm.ui.workspace.phone.parts.*;
import com.github.enerccio.lingollm.utils.UIUtils;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.util.HashMap;
import java.util.Map;

/**
 * Workspace for phones. The vertical tab sheet of {@link DesktopWorkspace} would take most of the screen, so the
 * parts are reached from a drawer (opened by the toggle in the top bar) and the active part fills the whole screen.
 * The models and the trash are the phone specific {@link PhoneAIPart} and {@link PhoneTrashPart}, the settings are the {@link PhoneSettingsPart}, other parts (the {@link LanguagePart} too) are shared with the desktop.
 */
@Configurable
@Extendable
public class PhoneWorkspace implements Workspace {

    @Autowired
    private Localization loc;

    @Autowired
    private UserService userService;

    @Autowired
    private User currentUser;

    private final LanguagePart languagePart = new LanguagePart(this);
    private final PhoneSettingsPart settingsPart = new PhoneSettingsPart(this);
    private final PhoneAIPart aiPart = new PhoneAIPart(this);
    private final ResourcesPart resourcesPart = new ResourcesPart(this);
    private final PhoneTrashPart trashPart = new PhoneTrashPart(this);

    private final Map<Tab, WorkspaceComponent> tabToComponent = new HashMap<>();
    private final Map<Tab, Component> tabToContent = new HashMap<>();
    private final Map<Tab, String> tabToTitle = new HashMap<>();

    private AppLayout layout;
    private DrawerToggle drawerToggle;
    private Span title;
    private Tabs tabs;
    private WorkspaceComponent activeComponent;
    private Button changePasswordButton;
    private Button logoutButton;
    private LanguageSelector languageSelector;
    private boolean navigationLocked;
    private Runnable onLogout;

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Component.class),
            locals = {@APILocals(name = "drawer", type = VerticalLayout.class),
                    @APILocals(name = "first", type = Tab.class)})
    @Override
    public Component create() throws Exception {
        layout = new AppLayout();
        layout.addClassName(CssStyles.FULL_SIZE);

        drawerToggle = new DrawerToggle();
        title = new Span();
        title.addClassName(CssStyles.SECTION_TITLE);
        layout.addToNavbar(drawerToggle, title);

        tabs = new Tabs();
        tabs.setOrientation(Tabs.Orientation.VERTICAL);
        tabs.setWidthFull();

        addTab(L.LABEL_LEARNING, languagePart);
        addTab(L.LABEL_SETTINGS, settingsPart);
        addTab(L.LABEL_MODELS, aiPart);
        addTab(L.LABEL_RESOURCES, resourcesPart);
        addTab(L.LABEL_TRASH, trashPart);

        tabs.addSelectedChangeListener(e -> {
            Tab selected = e.getSelectedTab();
            show(tabToComponent.get(selected), tabToContent.get(selected), tabToTitle.get(selected));
        });

        VerticalLayout drawer = new VerticalLayout();
        drawer.setSizeFull();
        drawer.setPadding(false);
        drawer.setSpacing(false);
        drawer.add(tabs);
        drawer.setFlexGrow(1, tabs);
        drawer.add(createFooter());
        layout.addToDrawer(drawer);

        Tab first = tabs.getSelectedTab();
        show(tabToComponent.get(first), tabToContent.get(first), tabToTitle.get(first));

        return layout;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "label", type = L.class),
                    @APIParameter(name = "part", type = WorkspaceComponent.class)},
            locals = {@APILocals(name = "name", type = String.class),
                    @APILocals(name = "tab", type = Tab.class)})
    private void addTab(L label, WorkspaceComponent part) throws Exception {
        String name = loc.getValue(label);
        Tab tab = new Tab(name);
        tabs.add(tab);
        tabToComponent.put(tab, part);
        tabToContent.put(tab, part.create());
        tabToTitle.put(tab, name);
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "part", type = WorkspaceComponent.class),
                    @APIParameter(name = "content", type = Component.class),
                    @APIParameter(name = "name", type = String.class)},
            locals = {@APILocals(name = "ex", type = Exception.class)})
    private void show(WorkspaceComponent part, Component content, String name) {
        if (activeComponent != null) {
            try {
                activeComponent.onTabClosed();
            } catch (Exception ex) {
                UIUtils.internalServerError(loc, ex);
            }
        }

        layout.setContent(content);
        title.setText(name);
        layout.setDrawerOpened(false);
        activeComponent = part;
        try {
            part.onTabSwitched();
        } catch (Exception ex) {
            UIUtils.internalServerError(loc, ex);
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Component.class),
            locals = {@APILocals(name = "footer", type = VerticalLayout.class)})
    private Component createFooter() {
        VerticalLayout footer = new VerticalLayout();
        footer.setSpacing(true);
        footer.setWidthFull();

        languageSelector = new LanguageSelector();
        languageSelector.setWidthFull();
        footer.add(languageSelector);

        changePasswordButton = new Button(loc.getValue(L.LABEL_CHANGE_PASSWORD), Solid.KEY.create());
        changePasswordButton.setWidthFull();
        changePasswordButton.addClickListener(e -> {
            try {
                UserDialog dialog = new UserDialog(userService.find(currentUser.getId()), true);
                dialog.setSelfEdit(true);
                dialog.create();
                dialog.open();
            } catch (Exception ex) {
                UIUtils.internalServerError(loc, ex);
            }
        });
        footer.add(changePasswordButton);

        if (onLogout != null) {
            logoutButton = new Button(loc.getValue(L.LABEL_LOGOUT), Solid.SIGN_OUT_ALT.create());
            logoutButton.setWidthFull();
            logoutButton.addClickListener(e -> onLogout.run());
            footer.add(logoutButton);
        }

        return footer;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class))
    @Override
    public void refresh() throws Exception {
        languagePart.refresh();
        settingsPart.refresh();
        aiPart.refresh();
        resourcesPart.refresh();
        trashPart.refresh();
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "locked", type = boolean.class)},
            locals = {@APILocals(name = "tab", type = Tab.class)})
    @Override
    public void setNavigationLocked(boolean locked) {
        navigationLocked = locked;
        drawerToggle.setEnabled(!locked);
        if (locked) {
            layout.setDrawerOpened(false);
        }
        for (Tab tab : tabToComponent.keySet()) {
            tab.setEnabled(!locked);
        }
        if (languageSelector != null) {
            languageSelector.setEnabled(!locked);
        }
        if (logoutButton != null) {
            logoutButton.setEnabled(!locked);
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = boolean.class))
    @Override
    public boolean isNavigationLocked() {
        return navigationLocked;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Runnable.class))
    @Override
    public Runnable getOnLogout() {
        return onLogout;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "onLogout", type = Runnable.class)})
    @Override
    public void setOnLogout(Runnable onLogout) {
        this.onLogout = onLogout;
    }

}
