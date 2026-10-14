package com.github.enerccio.lingollm.ui.widgets;

import com.flowingcode.vaadin.addons.fontawesome.FontAwesome.Solid;
import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.security.service.UserService;
import com.github.enerccio.lingollm.domain.service.CleanupService.EntityKey;
import com.github.enerccio.lingollm.domain.service.TrashService;
import com.github.enerccio.lingollm.domain.service.TrashService.*;
import com.github.enerccio.lingollm.domain.traits.*;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.ui.dialogs.ConfirmDialog;
import com.github.enerccio.lingollm.utils.UIUtils;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridMultiSelectionModel;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.util.*;
import java.util.stream.Stream;

/**
 * Deleted objects that can be restored, in a lazy table with a check box per object. Filters by type of the object
 * and, for administrators (<code>allUsers</code>), by its owner. Restoring the selection is refused as a whole when
 * a selected object depends on a deleted object that is not selected, see {@link TrashService}.
 * <p>
 * The widget only shows what {@link TrashService} allows the current user to see, <code>allUsers</code> just adds the
 * owner filter and column.
 */
@Configurable
@Extendable
public class TrashGrid extends VerticalLayout {

    @Autowired
    protected Localization loc;

    @Autowired
    private TrashService trashService;

    @Autowired
    private UserService userService;

    protected final boolean allUsers;
    private final List<Runnable> restoreListeners = new ArrayList<>();

    protected Grid<TrashItem> grid;
    protected ComboBox<User> userFilter;
    protected ComboBox<Class<?>> typeFilter;
    protected Button restoreButton;
    protected Button deleteButton;
    private Set<Class<?>> deletableTypes = Set.of();

    /**
     * @param allUsers show the owner filter and column, for administrators
     */
    public TrashGrid(boolean allUsers) {
        this.allUsers = allUsers;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = TrashGrid.class),
            locals = {@APILocals(name = "toolbar", type = Component.class),
                    @APILocals(name = "selection", type = GridMultiSelectionModel.class),
                    @APILocals(name = "description", type = Component.class),
                    @APILocals(name = "users", type = List.class)})
    public TrashGrid create() throws Exception {
        setSizeFull();
        setPadding(false);
        setSpacing(true);

        Component toolbar = createToolbar();

        grid = new Grid<>(TrashItem.class, false);
        grid.setSizeFull();
        GridMultiSelectionModel<TrashItem> selection = (GridMultiSelectionModel<TrashItem>) grid.setSelectionMode(Grid.SelectionMode.MULTI);
        selection.setSelectAllCheckboxVisibility(GridMultiSelectionModel.SelectAllCheckboxVisibility.VISIBLE);
        grid.setEmptyStateText(loc.getValue(L.MSG_TRASH_EMPTY));
        // selection survives loading other pages of the lazy table, items are identified by what they are
        grid.setItems(new CallbackDataProvider<TrashItem, Void>(this::fetch, query -> count(), TrashItem::key));
        deletableTypes = Set.copyOf(trashService.getPermanentlyDeletableTypes());
        grid.addSelectionListener(e -> {
            Set<TrashItem> selected = e.getAllSelectedItems();
            restoreButton.setEnabled(!selected.isEmpty());
            // only small objects can be deleted for good, a selection with anything else can only be restored
            deleteButton.setEnabled(!selected.isEmpty() && selected.stream().allMatch(item -> deletableTypes.contains(item.key().type())));
        });

        addColumns();

        Component description = createDescription();
        if (description != null) {
            add(description);
        }
        add(toolbar, grid);
        setFlexGrow(1, grid);

        if (allUsers) {
            List<User> users = new ArrayList<>(userService.findAll());
            users.sort(Comparator.comparing(User::getLogin, String.CASE_INSENSITIVE_ORDER));
            userFilter.setItems(users);
        }
        typeFilter.setItems(trashService.getTypes().stream()
                .sorted(Comparator.comparing(loc::localizeDomainObject, loc.createLocaleComparator())).toList());
        return this;
    }

    /**
     * Text above the table, null for none.
     */
    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Component.class),
            locals = {@APILocals(name = "description", type = Span.class)})
    protected Component createDescription() {
        Span description = new Span(loc.getValue(L.MSG_TRASH_DESCRIPTION));
        description.addClassName(CssStyles.TEXT_SECONDARY);
        return description;
    }

    /**
     * Filters and buttons above the table, creates {@link #userFilter} (for administrators), {@link #typeFilter} and
     * {@link #restoreButton} and {@link #deleteButton}.
     */
    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Component.class),
            locals = {@APILocals(name = "toolbar", type = HorizontalLayout.class),
                    @APILocals(name = "refreshButton", type = Button.class),
                    @APILocals(name = "spacer", type = Div.class)})
    protected Component createToolbar() {
        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setWidthFull();
        toolbar.setAlignItems(FlexComponent.Alignment.END);

        if (allUsers) {
            userFilter = new ComboBox<>(loc.getValue(L.LABEL_USER));
            userFilter.setPlaceholder(loc.getValue(L.LABEL_ALL_USERS));
            userFilter.setClearButtonVisible(true);
            userFilter.setItemLabelGenerator(User::getLogin);
            userFilter.addValueChangeListener(e -> refreshGrid());
            toolbar.add(userFilter);
        }

        typeFilter = new ComboBox<>(loc.getValue(L.LABEL_TYPE));
        typeFilter.setPlaceholder(loc.getValue(L.LABEL_ALL_TYPES));
        typeFilter.setClearButtonVisible(true);
        typeFilter.setItemLabelGenerator(loc::localizeDomainObject);
        typeFilter.addValueChangeListener(e -> refreshGrid());
        toolbar.add(typeFilter);

        Button refreshButton = new Button(loc.getValue(L.LABEL_REFRESH), Solid.SYNC.create(), e -> refresh());
        restoreButton = new Button(loc.getValue(L.LABEL_RESTORE_SELECTED), Solid.UNDO.create(), e -> restoreSelected());
        restoreButton.setThemeName("primary");
        restoreButton.setEnabled(false);

        deleteButton = createDeleteButton();

        Div spacer = new Div();
        toolbar.add(spacer, refreshButton, deleteButton, restoreButton);
        toolbar.setFlexGrow(1, spacer);
        return toolbar;
    }

    /**
     * Button that deletes the selected objects for good, enabled while only deletable objects are selected.
     */
    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Button.class),
            locals = {@APILocals(name = "button", type = Button.class)})
    protected Button createDeleteButton() {
        Button button = new Button(loc.getValue(L.LABEL_DELETE_PERMANENTLY), Solid.TRASH.create(), e -> deleteSelected());
        button.addThemeVariants(ButtonVariant.LUMO_ERROR);
        button.setTooltipText(loc.getValue(L.MSG_DELETE_PERMANENTLY_HINT));
        button.setEnabled(false);
        return button;
    }

    /**
     * Columns of the table.
     */
    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class))
    protected void addColumns() {
        grid.addColumn(item -> loc.localizeDomainObject(item.key().type()))
                .setHeader(loc.getValue(L.LABEL_TYPE)).setFlexGrow(1);
        grid.addColumn(TrashItem::label)
                .setHeader(loc.getValue(L.LABEL_NAME)).setFlexGrow(3);
        if (allUsers) {
            grid.addColumn(TrashItem::ownerLogin)
                    .setHeader(loc.getValue(L.LABEL_USER)).setFlexGrow(1);
        }
        grid.addColumn(item -> item.deletedAt() != null ? loc.getDateHourFormat().format(item.deletedAt()) : "")
                .setHeader(loc.getValue(L.LABEL_SOFT_DELETED)).setFlexGrow(1);
        grid.addComponentColumn(this::contentLink)
                .setHeader("").setFlexGrow(0).setWidth("200px");
    }

    /**
     * Called after objects were restored, so the rest of the UI can show them again.
     */
    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "listener", type = Runnable.class)})
    public void addRestoreListener(Runnable listener) {
        restoreListeners.add(listener);
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class))
    public void refresh() {
        refreshGrid();
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class))
    protected void refreshGrid() {
        if (grid != null) {
            grid.deselectAll();
            grid.getDataProvider().refreshAll();
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = TrashService.TrashFilter.class),
            locals = {@APILocals(name = "user", type = User.class)})
    private TrashFilter filter() {
        User user = userFilter != null ? userFilter.getValue() : null;
        return new TrashFilter(user != null ? user.getId() : null, typeFilter.getValue());
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Stream.class),
            parameters = {@APIParameter(name = "query", type = com.vaadin.flow.data.provider.Query.class)},
            locals = {@APILocals(name = "e", type = Exception.class)})
    private Stream<TrashItem> fetch(com.vaadin.flow.data.provider.Query<TrashItem, Void> query) {
        try {
            return trashService.find(filter(), query.getOffset(), query.getLimit()).stream();
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
            return Stream.empty();
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = int.class),
            locals = {@APILocals(name = "e", type = Exception.class)})
    private int count() {
        try {
            return (int) Math.min(Integer.MAX_VALUE, trashService.count(filter()));
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
            return 0;
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Button.class),
            parameters = {@APIParameter(name = "item", type = TrashService.TrashItem.class)},
            locals = {@APILocals(name = "link", type = Button.class)})
    protected Button contentLink(TrashItem item) {
        if (!item.extendable()) {
            return null;
        }
        Button link = new Button(loc.getValue(L.LABEL_EXTENDED_CONTENT), e -> showExtendedContent(item));
        link.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        return link;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "item", type = TrashService.TrashItem.class)},
            locals = {@APILocals(name = "json", type = String.class),
                    @APILocals(name = "dialog", type = Dialog.class),
                    @APILocals(name = "text", type = TextArea.class),
                    @APILocals(name = "e", type = Exception.class)})
    private void showExtendedContent(TrashItem item) {
        try {
            String json = trashService.getExtendedContent(item.key());
            if (json == null) {
                Notification.warning(loc.getValue(L.MSG_NO_EXTENDED_CONTENT));
                return;
            }
            Dialog dialog = new Dialog();
            dialog.setHeaderTitle(loc.getValue(L.LABEL_EXTENDED_CONTENT) + ": " + item.label());
            dialog.setWidth("800px");
            dialog.setResizable(true);

            TextArea text = new TextArea();
            text.setValue(json);
            text.setReadOnly(true);
            text.setWidthFull();
            text.setHeight("500px");
            text.addClassName(CssStyles.TEXT_MONOSPACE_LUMO);
            dialog.add(text);
            dialog.getFooter().add(new Button(loc.getValue(L.LABEL_CLOSE), e -> dialog.close()));
            dialog.open();
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            locals = {@APILocals(name = "keys", type = List.class)})
    protected void restoreSelected() {
        List<EntityKey> keys = grid.getSelectedItems().stream().map(TrashItem::key).toList();
        if (keys.isEmpty()) {
            return;
        }
        ConfirmDialog.show(String.format(loc.getValue(L.MSG_CONFIRM_RESTORE), keys.size()), () -> restore(keys));
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "keys", type = List.class)},
            locals = {@APILocals(name = "result", type = TrashService.RestoreResult.class),
                    @APILocals(name = "e", type = Exception.class)})
    private void restore(List<EntityKey> keys) {
        try {
            RestoreResult result = trashService.restore(keys);
            if (result.isBlocked()) {
                showBlocked(result);
                return;
            }
            Notification.success(String.format(loc.getValue(L.MSG_RESTORED), result.getRestored().size()));
            refreshGrid();
            restoreListeners.forEach(Runnable::run);
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            locals = {@APILocals(name = "keys", type = List.class)})
    protected void deleteSelected() {
        List<EntityKey> keys = grid.getSelectedItems().stream().map(TrashItem::key).toList();
        if (keys.isEmpty()) {
            return;
        }
        ConfirmDialog.show(String.format(loc.getValue(L.MSG_CONFIRM_DELETE_PERMANENTLY), keys.size()), () -> deletePermanently(keys));
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "keys", type = List.class)},
            locals = {@APILocals(name = "result", type = TrashService.DeleteResult.class),
                    @APILocals(name = "e", type = Exception.class)})
    private void deletePermanently(List<EntityKey> keys) {
        try {
            DeleteResult result = trashService.deletePermanently(keys);
            if (result.isBlocked()) {
                showDeleteBlocked(result);
                return;
            }
            Notification.success(String.format(loc.getValue(L.MSG_DELETED_PERMANENTLY), result.getDeleted().size()));
            refreshGrid();
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "result", type = TrashService.DeleteResult.class)},
            locals = {@APILocals(name = "dialog", type = Dialog.class),
                    @APILocals(name = "content", type = VerticalLayout.class),
                    @APILocals(name = "blocked", type = Map.Entry.class),
                    @APILocals(name = "key", type = com.github.enerccio.lingollm.domain.service.CleanupService.EntityKey.class),
                    @APILocals(name = "reason", type = String.class)})
    private void showDeleteBlocked(DeleteResult result) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(loc.getValue(L.LABEL_DELETE_PERMANENTLY));
        dialog.setWidth("600px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.add(new Span(loc.getValue(L.MSG_DELETE_PERMANENTLY_BLOCKED)));
        for (Map.Entry<EntityKey, List<String>> blocked : result.getBlocked().entrySet()) {
            EntityKey key = blocked.getKey();
            for (String reason : blocked.getValue()) {
                content.add(new Span(String.format(loc.getValue(L.MSG_DELETE_PERMANENTLY_BLOCKED_ITEM),
                        loc.localizeDomainObject(key.type()), result.getBlockedLabels().get(key), reason)));
            }
        }
        dialog.add(content);
        dialog.getFooter().add(new Button(loc.getValue(L.LABEL_CLOSE), e -> dialog.close()));
        dialog.open();
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "result", type = TrashService.RestoreResult.class)},
            locals = {@APILocals(name = "dialog", type = Dialog.class),
                    @APILocals(name = "content", type = VerticalLayout.class),
                    @APILocals(name = "blocked", type = Map.Entry.class),
                    @APILocals(name = "key", type = com.github.enerccio.lingollm.domain.service.CleanupService.EntityKey.class),
                    @APILocals(name = "blocker", type = TrashService.Blocker.class)})
    private void showBlocked(RestoreResult result) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(loc.getValue(L.LABEL_RESTORE));
        dialog.setWidth("600px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.add(new Span(loc.getValue(L.MSG_RESTORE_BLOCKED)));
        for (Map.Entry<EntityKey, List<Blocker>> blocked : result.getBlocked().entrySet()) {
            EntityKey key = blocked.getKey();
            for (Blocker blocker : blocked.getValue()) {
                content.add(new Span(String.format(loc.getValue(L.MSG_RESTORE_BLOCKED_ITEM),
                        loc.localizeDomainObject(key.type()), result.getBlockedLabels().get(key),
                        loc.localizeDomainObject(blocker.parent().type()), blocker.label(), blocker.field())));
            }
        }
        dialog.add(content);
        dialog.getFooter().add(new Button(loc.getValue(L.LABEL_CLOSE), e -> dialog.close()));
        dialog.open();
    }
}
