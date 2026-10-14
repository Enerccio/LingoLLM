package com.github.enerccio.lingollm.ui.workspace;

import com.flowingcode.vaadin.addons.fontawesome.FontAwesome.Solid;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.security.service.UserService;
import com.github.enerccio.lingollm.domain.traits.*;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.ui.dialogs.UserDialog;
import com.github.enerccio.lingollm.ui.widgets.HTabSheet;
import com.github.enerccio.lingollm.ui.widgets.LanguageSelector;
import com.github.enerccio.lingollm.ui.workspace.parts.*;
import com.github.enerccio.lingollm.utils.UIUtils;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.util.HashMap;
import java.util.Map;

@Configurable
@Extendable
public class DesktopWorkspace implements Workspace {

    @Autowired
    private Localization loc;

    @Autowired
    private UserService userService;

    @Autowired
    private User currentUser;

    private HTabSheet tabs;

    private final Map<Tab, WorkspaceComponent> tabToComponent = new HashMap<>();

    private final LanguagePart languagePart = new LanguagePart(this);
    private Component languagePartComponent;

    private final UserPart userPart = new UserPart(this);
    private Component userPartComponent;

    private final AIPart aiPart = new AIPart(this);
    private Component aiPartComponent;

    private final ResourcesPart resourcesPart = new ResourcesPart(this);
    private Component resourcesPartComponent;

    private final TrashPart trashPart = new TrashPart(this);
    private Component trashPartComponent;

    private final AdminPart adminPart = new AdminPart(this);
    private Component adminPartComponent;

    private WorkspaceComponent activeComponent;
    private Button adminButton;
    private Button logoutButton;
    private LanguageSelector languageSelector;
    private boolean navigationLocked;
    private Runnable onLogout;

    public DesktopWorkspace() {

    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Component.class),
            locals = {@APILocals(name = "vl", type = VerticalLayout.class),
                    @APILocals(name = "languageTabHeader", type = HorizontalLayout.class),
                    @APILocals(name = "languageNameSpan", type = Span.class),
                    @APILocals(name = "languageTab", type = Tab.class),
                    @APILocals(name = "userTabHeader", type = HorizontalLayout.class),
                    @APILocals(name = "userNameSpan", type = Span.class),
                    @APILocals(name = "userTab", type = Tab.class),
                    @APILocals(name = "aiTabHeader", type = HorizontalLayout.class),
                    @APILocals(name = "connNameSpan", type = Span.class),
                    @APILocals(name = "aiTab", type = Tab.class),
                    @APILocals(name = "resourcesTabHeader", type = HorizontalLayout.class),
                    @APILocals(name = "resourcesTab", type = Tab.class),
                    @APILocals(name = "trashTabHeader", type = HorizontalLayout.class),
                    @APILocals(name = "trashTab", type = Tab.class),
                    @APILocals(name = "user", type = User.class)})
    @Override
    public Component create() throws Exception {
        VerticalLayout vl = new VerticalLayout();
        vl.setSizeFull();

        tabs = new HTabSheet();
        tabs.setSizeFull();
        tabs.setTabsWidth("260px");
        tabs.setTabsMaxWidth("300px");
        vl.add(tabs);

        languagePartComponent = languagePart.create();
        userPartComponent = userPart.create();
        aiPartComponent = aiPart.create();
        resourcesPartComponent = resourcesPart.create();
        trashPartComponent = trashPart.create();
        adminPartComponent = adminPart.create();

        HorizontalLayout languageTabHeader = new HorizontalLayout();
        languageTabHeader.setAlignItems(Alignment.CENTER);
        languageTabHeader.setSpacing(true);
        Span languageNameSpan = new Span(loc.getValue(L.LABEL_LEARNING));
        languageTabHeader.add(languageNameSpan);

        Tab languageTab = tabs.add(languageTabHeader, languagePartComponent);
        tabToComponent.put(languageTab, languagePart);

        HorizontalLayout userTabHeader = new HorizontalLayout();
        userTabHeader.setAlignItems(Alignment.CENTER);
        userTabHeader.setSpacing(true);
        Span userNameSpan = new Span(loc.getValue(L.LABEL_SETTINGS));
        userTabHeader.add(userNameSpan);

        Tab userTab = tabs.add(userTabHeader, userPartComponent);
        tabToComponent.put(userTab, userPart);

        HorizontalLayout aiTabHeader = new HorizontalLayout();
        aiTabHeader.setAlignItems(Alignment.CENTER);
        aiTabHeader.setSpacing(true);
        Span connNameSpan = new Span(loc.getValue(L.LABEL_MODELS));
        aiTabHeader.add(connNameSpan);

        Tab aiTab = tabs.add(aiTabHeader, aiPartComponent);
        tabToComponent.put(aiTab, aiPart);

        HorizontalLayout resourcesTabHeader = new HorizontalLayout();
        resourcesTabHeader.setAlignItems(Alignment.CENTER);
        resourcesTabHeader.setSpacing(true);
        resourcesTabHeader.add(new Span(loc.getValue(L.LABEL_RESOURCES)));

        Tab resourcesTab = tabs.add(resourcesTabHeader, resourcesPartComponent);
        tabToComponent.put(resourcesTab, resourcesPart);

        HorizontalLayout trashTabHeader = new HorizontalLayout();
        trashTabHeader.setAlignItems(Alignment.CENTER);
        trashTabHeader.setSpacing(true);
        trashTabHeader.add(new Span(loc.getValue(L.LABEL_TRASH)));

        Tab trashTab = tabs.add(trashTabHeader, trashPartComponent);
        tabToComponent.put(trashTab, trashPart);

        User user = userService.find(currentUser.getId());
        tabs.setFooterComponent(createFooter(user));

        tabs.addSelectedChangeListener(e -> {
            if (activeComponent != null) {
                try {
                    activeComponent.onTabClosed();
                } catch (Exception ex) {
                    UIUtils.internalServerError(loc, ex);
                }
            }

            WorkspaceComponent workspaceComponent = tabToComponent.get(e.getSelectedTab());
            activeComponent = workspaceComponent;
            if (workspaceComponent != null) {
                try {
                    workspaceComponent.onTabSwitched();
                } catch (Exception ex) {
                    UIUtils.internalServerError(loc, ex);
                }
            }
        });

        activeComponent = languagePart;

        return vl;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Component.class),
            parameters = {@APIParameter(name = "user", type = User.class)},
            locals = {@APILocals(name = "footer", type = VerticalLayout.class),
                    @APILocals(name = "changePasswordButton", type = Button.class)})
    private Component createFooter(User user) {
        VerticalLayout footer = new VerticalLayout();
        footer.setPadding(false);
        footer.setSpacing(true);
        footer.setWidthFull();

        if (user != null && user.isAdmin()) {
            footer.add(createAdminFooter());
        }

        languageSelector = new LanguageSelector();
        languageSelector.setWidthFull();
        footer.add(languageSelector);

        Button changePasswordButton = new Button(loc.getValue(L.LABEL_CHANGE_PASSWORD), Solid.KEY.create());
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
            returns = @APIReturn(type = Component.class))
    private Component createAdminFooter() {
        adminButton = new Button(loc.getValue(L.LABEL_ADMIN), Solid.USER_TIE.create());
        adminButton.setWidthFull();
        adminButton.addClickListener(e -> {
            if (activeComponent != null) {
                try {
                    activeComponent.onTabClosed();
                } catch (Exception ex) {
                    UIUtils.internalServerError(loc, ex);
                }
            }
            tabs.setCustomContent(adminPartComponent);
            activeComponent = adminPart;
            try {
                adminPart.onTabSwitched();
            } catch (Exception ex) {
                UIUtils.internalServerError(loc, ex);
            }
        });
        return adminButton;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "locked", type = boolean.class)},
            locals = {@APILocals(name = "selectedTab", type = Tab.class),
                    @APILocals(name = "tab", type = Tab.class)})
    @Override
    public void setNavigationLocked(boolean locked) {
        navigationLocked = locked;
        Tab selectedTab = tabs.getSelectedTab();
        for (Tab tab : tabToComponent.keySet()) {
            tab.setEnabled(!locked || tab.equals(selectedTab));
        }
        if (adminButton != null) {
            adminButton.setEnabled(!locked);
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

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class))
    @Override
    public void refresh() throws Exception {
        languagePart.refresh();
        userPart.refresh();
        aiPart.refresh();
        resourcesPart.refresh();
        trashPart.refresh();
        adminPart.refresh();
    }

}