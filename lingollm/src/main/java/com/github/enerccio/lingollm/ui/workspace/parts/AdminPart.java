package com.github.enerccio.lingollm.ui.workspace.parts;

import com.flowingcode.vaadin.addons.fontawesome.FontAwesome.Solid;
import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.UIConstants;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.security.service.UserService;
import com.github.enerccio.lingollm.domain.service.AIService;
import com.github.enerccio.lingollm.domain.service.ExtensionVerificationException;
import com.github.enerccio.lingollm.domain.service.OsgiService;
import com.github.enerccio.lingollm.domain.service.OsgiService.ExtensionReport;
import com.github.enerccio.lingollm.domain.traits.*;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.ui.JavascriptLibrary;
import com.github.enerccio.lingollm.ui.dialogs.ConfirmDialog;
import com.github.enerccio.lingollm.ui.dialogs.ExtensionLogDialog;
import com.github.enerccio.lingollm.ui.dialogs.UserDialog;
import com.github.enerccio.lingollm.ui.workspace.Workspace;
import com.github.enerccio.lingollm.ui.workspace.WorkspaceComponent;
import com.github.enerccio.lingollm.ui.workspace.parts.admin.CleanupPanel;
import com.github.enerccio.lingollm.ui.workspace.parts.admin.DatabaseBackupPanel;
import com.github.enerccio.lingollm.utils.UIUtils;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.InMemoryUploadHandler;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

@Configurable
@Extendable
public class AdminPart implements WorkspaceComponent {

    private static final Logger log = LoggerFactory.getLogger(AdminPart.class);

    private final Workspace workspace;

    @Autowired
    private OsgiService osgiService;

    @Autowired
    private UserService userService;

    @Autowired
    private AIService aiService;

    @Autowired
    private User currentUser;

    @Autowired
    private Localization loc;

    private Grid<Bundle> extensionsGrid;
    private Grid<User> usersGrid;
    private final DatabaseBackupPanel databaseBackupPanel = new DatabaseBackupPanel();
    private final CleanupPanel cleanupPanel = new CleanupPanel();

    public AdminPart(Workspace workspace) {
        this.workspace = workspace;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Component.class),
            locals = {@APILocals(name = "tabSheet", type = TabSheet.class),
                    @APILocals(name = "usersTab", type = Tab.class),
                    @APILocals(name = "usersContent", type = Component.class),
                    @APILocals(name = "databaseTab", type = Tab.class),
                    @APILocals(name = "cleanupTab", type = Tab.class),
                    @APILocals(name = "extensionsTab", type = Tab.class),
                    @APILocals(name = "extensionsContent", type = Component.class),
                    @APILocals(name = "container", type = Div.class)})
    @Override
    public Component create() throws Exception {
        TabSheet tabSheet = new TabSheet();
        tabSheet.setSizeFull();

        Tab usersTab = new Tab(loc.getValue(L.LABEL_USERS));
        Component usersContent = createUsersTab();
        tabSheet.add(usersTab, usersContent);

        Tab databaseTab = new Tab(loc.getValue(L.LABEL_DATABASE_BACKUPS));
        tabSheet.add(databaseTab, databaseBackupPanel.create());

        Tab cleanupTab = new Tab(loc.getValue(L.LABEL_CLEANUP));
        tabSheet.add(cleanupTab, cleanupPanel.create());

        Tab extensionsTab = new Tab(loc.getValue(L.LABEL_EXTENSIONS));
        Component extensionsContent = createExtensionsTab();
        tabSheet.add(extensionsTab, extensionsContent);

        Div container = new Div(tabSheet);
        container.setSizeFull();
        return container;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Component.class),
            locals = {@APILocals(name = "layout", type = VerticalLayout.class),
                    @APILocals(name = "toolbar", type = HorizontalLayout.class),
                    @APILocals(name = "addUserButton", type = Button.class),
                    @APILocals(name = "userAisCheckbox", type = Checkbox.class)})
    private Component createUsersTab() throws Exception {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();

        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setWidthFull();
        toolbar.setAlignItems(HorizontalLayout.Alignment.CENTER);

        Button addUserButton = new Button(loc.getValue(L.LABEL_ADD_USER), Solid.PLUS_CIRCLE.create(), e -> openUserDialog(new User()));
        addUserButton.setThemeName("primary");
        toolbar.add(addUserButton);

        // when off, users can only use the AIs the administrators share (the ones they already have keep working)
        Checkbox userAisCheckbox = new Checkbox(loc.getValue(L.LABEL_USERS_CAN_CREATE_AIS), aiService.isUserCreationAllowed());
        userAisCheckbox.addValueChangeListener(e -> {
            if (!e.isFromClient()) {
                return;
            }
            try {
                aiService.setUserCreationAllowed(e.getValue());
            } catch (Exception ex) {
                userAisCheckbox.setValue(e.getOldValue());
                UIUtils.internalServerError(loc, ex);
            }
        });
        toolbar.add(userAisCheckbox);

        usersGrid = new Grid<>(User.class, false);
        usersGrid.setSizeFull();

        usersGrid.addColumn(User::getLogin)
                .setHeader(loc.getValue(L.LABEL_USERNAME))
                .setFlexGrow(1);

        usersGrid.addColumn(User::getFullName)
                .setHeader(loc.getValue(L.LABEL_USER_FULLNAME))
                .setFlexGrow(1);

        usersGrid.addColumn(user -> loc.getValue(user.isAdmin() ? L.LABEL_YES : L.LABEL_NO))
                .setHeader(loc.getValue(L.LABEL_ADMINISTRATOR))
                .setFlexGrow(0)
                .setWidth("150px");

        usersGrid.addComponentColumn(user -> {
                    HorizontalLayout actions = new HorizontalLayout();
                    actions.setSpacing(true);

                    Button editButton = new Button(Solid.PEN.create(), e -> openUserDialog(user));

                    Button deleteButton = new Button(Solid.TRASH.create(), e -> deleteUser(user));
                    deleteButton.setThemeName("error tertiary");
                    deleteButton.setEnabled(!user.getId().equals(currentUser.getId()));

                    actions.add(editButton, deleteButton);
                    return actions;
                })
                .setHeader("")
                .setFlexGrow(0)
                .setWidth(UIConstants.TOOL_COLUMN_SIZE_HUGE);

        layout.add(toolbar, usersGrid);
        layout.setFlexGrow(1, usersGrid);
        refreshUsersGrid();

        return layout;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "user", type = User.class)},
            locals = {@APILocals(name = "dialog", type = UserDialog.class)})
    private void openUserDialog(User user) {
        UserDialog dialog = new UserDialog(user, false);
        dialog.setOnSave(this::refreshUsersGrid);
        dialog.create();
        dialog.open();
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "user", type = User.class)},
            locals = {@APILocals(name = "e", type = Exception.class)})
    private void deleteUser(User user) {
        try {
            if (user.getId().equals(currentUser.getId())) {
                com.github.enerccio.lingollm.ui.widgets.Notification.warning(loc.getValue(L.MSG_CANNOT_DELETE_SELF));
                return;
            }
            if (userService.isLastAdmin(user)) {
                com.github.enerccio.lingollm.ui.widgets.Notification.warning(loc.getValue(L.MSG_CANNOT_DELETE_LAST_ADMIN));
                return;
            }
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
            return;
        }
        ConfirmDialog.show(loc.getValue(L.MSG_CONFIRM_DELETE), () -> {
            try {
                userService.deleteUser(user);
                refreshUsersGrid();
            } catch (Exception e) {
                UIUtils.internalServerError(loc, e);
            }
        });
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            locals = {@APILocals(name = "users", type = List.class),
                    @APILocals(name = "e", type = Exception.class)})
    private void refreshUsersGrid() {
        if (usersGrid == null) {
            return;
        }
        try {
            List<User> users = userService.findAll();
            users.sort(Comparator.comparing(User::getLogin, String.CASE_INSENSITIVE_ORDER));
            usersGrid.setItems(users);
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Component.class),
            locals = {@APILocals(name = "layout", type = VerticalLayout.class),
                    @APILocals(name = "toolbar", type = HorizontalLayout.class),
                    @APILocals(name = "handler", type = InMemoryUploadHandler.class),
                    @APILocals(name = "upload", type = Upload.class),
                    @APILocals(name = "refreshButton", type = Button.class)})
    private Component createExtensionsTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();

        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setWidthFull();
        toolbar.setAlignItems(HorizontalLayout.Alignment.CENTER);

        InMemoryUploadHandler handler = new InMemoryUploadHandler((metadata, data) -> {
            try {
                osgiService.installPackage(metadata.fileName(), data);

                Notification.show(loc.getValue(L.MSG_EXTENSION_INSTALLED_SUCCESS));
                refreshGrid();
            } catch (ExtensionVerificationException e) {
                log.error("Extension {} was rejected:\n{}", e.getExtension(), e.getReport());
                refreshGrid();
                Notification.show(String.format(loc.getValue(L.ERROR_EXTENSION_INCOMPATIBLE), e.getExtension()),
                        5000, Notification.Position.MIDDLE);
                showReport(e.getReport());
            } catch (Exception e) {
                log.error("Failed to load extension bundle", e);
                refreshGrid();
                Notification.show(
                        String.format(loc.getValue(L.ERROR_EXTENSION_INSTALL_FAILED), e.getMessage()),
                        5000,
                        Notification.Position.MIDDLE
                );
            }
        });

        Upload upload = new Upload(handler);
        upload.setAcceptedFileExtensions(".jar");
        upload.setAcceptedMimeTypes("application/zip", "application/jar", "application/java-archive", "application/x-java-archive");
        upload.setUploadButton(new Button(loc.getValue(L.LABEL_UPLOAD_EXTENSION), VaadinIcon.UPLOAD.create()));
        upload.setDropLabel(new Div(new Button(loc.getValue(L.LABEL_UPLOAD_EXTENSION))));

        Button refreshButton = new Button(loc.getValue(L.LABEL_REFRESH), VaadinIcon.REFRESH.create());
        refreshButton.addClickListener(e -> refreshGrid());

        toolbar.add(upload, refreshButton);

        extensionsGrid = new Grid<>();
        extensionsGrid.setSizeFull();

        extensionsGrid.addColumn(Bundle::getBundleId)
                .setHeader(loc.getValue(L.LABEL_BUNDLE_ID))
                .setFlexGrow(0)
                .setWidth("80px");

        extensionsGrid.addColumn(bundle -> {
                    String name = bundle.getHeaders().get("Bundle-Name");
                    return (name != null && !name.isBlank()) ? name : bundle.getSymbolicName();
                })
                .setHeader(loc.getValue(L.LABEL_BUNDLE_NAME))
                .setFlexGrow(1);

        extensionsGrid.addComponentColumn(bundle -> {
                    HorizontalLayout version = new HorizontalLayout(new Span(bundle.getVersion().toString()));
                    version.setAlignItems(HorizontalLayout.Alignment.CENTER);
                    version.setSpacing(false);
                    version.addClassName(CssStyles.GAP_S);
                    try {
                        String sha256 = osgiService.getSha256(bundle);
                        Button hashButton = new Button("#");
                        hashButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
                        UIUtils.addTooltip(hashButton, loc.getValue(L.LABEL_SHA256) + ": " + sha256);
                        hashButton.addClickListener(e -> {
                            JavascriptLibrary.copyToClipboard(e.getSource().getUI().orElseThrow().getPage(), sha256);
                            Notification.show(loc.getValue(L.MSG_COPIED_TO_CLIPBOARD));
                        });
                        version.add(hashButton);
                    } catch (IOException e) {
                        log.warn("Failed to compute SHA-256 of extension {}", extensionName(bundle), e);
                    }
                    return version;
                })
                .setHeader(loc.getValue(L.LABEL_BUNDLE_VERSION))
                .setFlexGrow(0)
                .setWidth("150px");

        extensionsGrid.addColumn(bundle -> osgiService.isStopped(bundle)
                        ? loc.getValue(L.ENUM_BUNDLE_STATE_STOPPED)
                        : formatBundleState(bundle.getState()))
                .setHeader(loc.getValue(L.LABEL_BUNDLE_STATE))
                .setFlexGrow(0)
                .setWidth("140px");

        extensionsGrid.addComponentColumn(bundle -> {
                    ExtensionReport report = osgiService.getVerificationReport(bundle);
                    if (report == null) {
                        return new Div();
                    }
                    Button reportButton = new Button(loc.getValue(report.valid() ? L.LABEL_VALID : L.LABEL_INVALID));
                    reportButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                    if (!report.valid()) {
                        reportButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
                    }
                    reportButton.addClickListener(e -> showReport(report.text()));
                    return reportButton;
                })
                .setHeader(loc.getValue(L.LABEL_BUNDLE_VERIFICATION))
                .setFlexGrow(0)
                .setWidth("140px");

        extensionsGrid.addComponentColumn(bundle -> {
                    boolean stopped = osgiService.isStopped(bundle);
                    Button toggleButton = stopped
                            ? new Button(loc.getValue(L.LABEL_START), VaadinIcon.PLAY.create())
                            : new Button(loc.getValue(L.LABEL_STOP), VaadinIcon.STOP.create());
                    toggleButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
                    toggleButton.addClickListener(e -> toggleExtension(bundle, stopped));

                    Button logButton = new Button(loc.getValue(L.LABEL_LOG), VaadinIcon.FILE_TEXT_O.create());
                    logButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
                    logButton.addClickListener(e -> ExtensionLogDialog.show(bundle, extensionName(bundle)));

                    Button unloadButton = new Button(loc.getValue(L.LABEL_UNLOAD_EXTENSION), VaadinIcon.TRASH.create());
                    unloadButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
                    unloadButton.addClickListener(e -> unloadExtension(bundle));
                    return new HorizontalLayout(toggleButton, logButton, unloadButton);
                })
                .setHeader(loc.getValue(L.LABEL_ACTIONS))
                .setFlexGrow(0)
                .setWidth("400px");

        layout.add(toolbar, extensionsGrid);
        refreshGrid();

        return layout;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "report", type = String.class)},
            locals = {@APILocals(name = "dialog", type = Dialog.class),
                    @APILocals(name = "text", type = TextArea.class),
                    @APILocals(name = "close", type = Button.class)})
    private void showReport(String report) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(loc.getValue(L.LABEL_VERIFICATION_REPORT));
        dialog.setWidth("900px");

        TextArea text = new TextArea();
        text.setValue(report);
        text.setReadOnly(true);
        text.setWidthFull();
        text.setHeight("400px");
        text.addClassName(CssStyles.TEXT_MONOSPACE);
        dialog.add(text);

        Button close = new Button(loc.getValue(L.LABEL_CLOSE), e -> dialog.close());
        dialog.getFooter().add(close);
        dialog.open();
    }

    private static String extensionName(Bundle bundle) {
        String name = bundle.getHeaders().get("Bundle-Name");
        return (name != null && !name.isBlank()) ? name : bundle.getSymbolicName();
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "bundle", type = Bundle.class),
                    @APIParameter(name = "stopped", type = boolean.class)},
            locals = {@APILocals(name = "e", type = ExtensionVerificationException.class),
                    @APILocals(name = "e", type = Exception.class)})
    private void toggleExtension(Bundle bundle, boolean stopped) {
        try {
            if (stopped) {
                osgiService.startExtension(bundle);
                Notification.show(loc.getValue(L.MSG_EXTENSION_STARTED));
            } else {
                osgiService.stopExtension(bundle);
                Notification.show(loc.getValue(L.MSG_EXTENSION_STOPPED));
            }
        } catch (ExtensionVerificationException e) {
            log.error("Extension {} was rejected:\n{}", e.getExtension(), e.getReport());
            Notification.show(String.format(loc.getValue(L.ERROR_EXTENSION_INCOMPATIBLE), e.getExtension()),
                    5000, Notification.Position.MIDDLE);
            showReport(e.getReport());
        } catch (Exception e) {
            log.error("Failed to {} extension", stopped ? "start" : "stop", e);
            Notification.show(
                    String.format(loc.getValue(stopped ? L.ERROR_EXTENSION_START_FAILED : L.ERROR_EXTENSION_STOP_FAILED), e.getMessage()),
                    5000,
                    Notification.Position.MIDDLE
            );
        }
        refreshGrid();
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "bundle", type = Bundle.class)})
    private void unloadExtension(Bundle bundle) {
        ConfirmDialog.show(String.format(loc.getValue(L.MSG_CONFIRM_UNLOAD_EXTENSION), extensionName(bundle)), () -> {
            try {
                osgiService.uninstallPackage(bundle);
                Notification.show(loc.getValue(L.MSG_EXTENSION_UNINSTALLED_SUCCESS));
            } catch (Exception e) {
                log.error("Failed to unload extension", e);
                Notification.show(
                        String.format(loc.getValue(L.ERROR_EXTENSION_UNINSTALL_FAILED), e.getMessage()),
                        5000,
                        Notification.Position.MIDDLE
                );
            }
            refreshGrid();
        });
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            locals = {@APILocals(name = "bundles", type = List.class)})
    private void refreshGrid() {
        if (extensionsGrid != null && osgiService != null) {
            List<Bundle> bundles = osgiService.getBundles();
            extensionsGrid.setItems(bundles);
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = String.class),
            parameters = {@APIParameter(name = "state", type = int.class)})
    private String formatBundleState(int state) {
        return switch (state) {
            case Bundle.UNINSTALLED -> loc.getValue(L.ENUM_BUNDLE_STATE_UNINSTALLED);
            case Bundle.INSTALLED -> loc.getValue(L.ENUM_BUNDLE_STATE_INSTALLED);
            case Bundle.RESOLVED -> loc.getValue(L.ENUM_BUNDLE_STATE_RESOLVED);
            case Bundle.STARTING -> loc.getValue(L.ENUM_BUNDLE_STATE_STARTING);
            case Bundle.STOPPING -> loc.getValue(L.ENUM_BUNDLE_STATE_STOPPING);
            case Bundle.ACTIVE -> loc.getValue(L.ENUM_BUNDLE_STATE_ACTIVE);
            default -> loc.getValue(L.ENUM_BUNDLE_STATE_UNKNOWN).formatted(state);
        };
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class))
    @Override
    public void refresh() throws Exception {
        refreshGrid();
        refreshUsersGrid();
        databaseBackupPanel.refresh();
        cleanupPanel.refresh();
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