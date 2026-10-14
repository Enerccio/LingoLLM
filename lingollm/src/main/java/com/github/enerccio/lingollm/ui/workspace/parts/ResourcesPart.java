package com.github.enerccio.lingollm.ui.workspace.parts;

import com.flowingcode.vaadin.addons.fontawesome.FontAwesome.Solid;
import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.domain.model.impl.Resource;
import com.github.enerccio.lingollm.domain.service.ResourceService;
import com.github.enerccio.lingollm.domain.service.ResourceService.ResourceLink;
import com.github.enerccio.lingollm.domain.traits.*;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.ui.components.MessageImages;
import com.github.enerccio.lingollm.ui.dialogs.ConfirmDialog;
import com.github.enerccio.lingollm.ui.widgets.Notification;
import com.github.enerccio.lingollm.ui.workspace.Workspace;
import com.github.enerccio.lingollm.ui.workspace.WorkspaceComponent;
import com.github.enerccio.lingollm.utils.UIUtils;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridMultiSelectionModel;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.QuerySortOrder;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.flow.server.streams.InMemoryUploadHandler;
import com.vaadin.flow.server.streams.InputStreamDownloadCallback;
import com.vaadin.flow.server.streams.InputStreamDownloadHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

/**
 * All files of the user (pictures of the parts for now) in a lazy table: download, replace the content, delete. A
 * deleted resource is only marked as deleted, cleanup removes it later and the file stays in the data folder.
 */
@Configurable
@Extendable
public class ResourcesPart implements WorkspaceComponent {

    @Autowired
    private Localization loc;

    @Autowired
    private ResourceService resourceService;

    private final Workspace workspace;
    private Grid<Resource> grid;
    private Button deleteSelectedButton;
    private MessageImages thumbnails;

    public ResourcesPart(Workspace workspace) {
        this.workspace = workspace;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = String.class))
    public String getName() {
        return loc.getValue(L.LABEL_RESOURCES);
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Component.class),
            locals = {@APILocals(name = "mainLayout", type = VerticalLayout.class),
                    @APILocals(name = "headerLayout", type = HorizontalLayout.class),
                    @APILocals(name = "titleSpan", type = Span.class),
                    @APILocals(name = "refreshButton", type = Button.class),
                    @APILocals(name = "buttons", type = HorizontalLayout.class),
                    @APILocals(name = "selection", type = GridMultiSelectionModel.class)})
    @Override
    public Component create() throws Exception {
        VerticalLayout mainLayout = new VerticalLayout();
        mainLayout.setSizeFull();
        mainLayout.setPadding(true);
        mainLayout.setSpacing(true);

        thumbnails = new MessageImages(List.of());

        HorizontalLayout headerLayout = new HorizontalLayout();
        headerLayout.setWidthFull();
        headerLayout.setAlignItems(FlexComponent.Alignment.CENTER);

        Span titleSpan = new Span(loc.getValue(L.LABEL_RESOURCES));
        titleSpan.addClassName(CssStyles.SECTION_TITLE);

        deleteSelectedButton = new Button(loc.getValue(L.LABEL_DELETE_SELECTED), Solid.TRASH.create(), event -> deleteSelected());
        deleteSelectedButton.setThemeName("error");
        deleteSelectedButton.setEnabled(false);

        Button refreshButton = new Button(loc.getValue(L.LABEL_REFRESH), Solid.SYNC.create(), event -> refreshGrid());

        HorizontalLayout buttons = new HorizontalLayout(deleteSelectedButton, refreshButton);
        headerLayout.add(titleSpan, buttons);
        headerLayout.setFlexGrow(1, titleSpan);
        headerLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        grid = new Grid<>(Resource.class, false);
        grid.setSizeFull();
        GridMultiSelectionModel<Resource> selection = (GridMultiSelectionModel<Resource>) grid.setSelectionMode(Grid.SelectionMode.MULTI);
        selection.setSelectAllCheckboxVisibility(GridMultiSelectionModel.SelectAllCheckboxVisibility.VISIBLE);
        grid.setEmptyStateText(loc.getValue(L.MSG_RESOURCES_EMPTY));
        grid.setMultiSort(false);
        // selection survives loading other pages of the lazy table
        grid.setItems(new CallbackDataProvider<Resource, Void>(this::fetch, query -> count(), Resource::getUuid));
        grid.addSelectionListener(event -> deleteSelectedButton.setEnabled(!event.getAllSelectedItems().isEmpty()));

        grid.addComponentColumn(resource -> resource.getMimeType() != null && resource.getMimeType().startsWith("image/")
                        ? thumbnails.figure(resource, "", "48px") : new Span())
                .setHeader("").setFlexGrow(0).setWidth("90px");

        grid.addColumn(Resource::getOriginalName)
                .setHeader(loc.getValue(L.LABEL_NAME)).setSortProperty("originalName").setFlexGrow(2);

        grid.addColumn(Resource::getMimeType)
                .setHeader(loc.getValue(L.LABEL_TYPE)).setSortProperty("mimeType").setFlexGrow(1);

        grid.addColumn(resource -> FileUtils.byteCountToDisplaySize(resource.getSize()))
                .setHeader(loc.getValue(L.LABEL_SIZE)).setSortProperty("size").setFlexGrow(0).setWidth("110px");

        grid.addColumn(resource -> loc.getDateHourFormat().format(resource.getCreation()))
                .setHeader(loc.getValue(L.LABEL_CREATED)).setSortProperty("creation").setFlexGrow(1);

        grid.addComponentColumn(this::linkOf)
                .setHeader(loc.getValue(L.LABEL_LINKED_OBJECT)).setFlexGrow(1);

        grid.addComponentColumn(this::actionsOf)
                .setHeader("").setFlexGrow(0).setWidth("190px");

        mainLayout.add(headerLayout, grid);
        mainLayout.setFlexGrow(1, grid);
        return mainLayout;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Stream.class),
            parameters = {@APIParameter(name = "query", type = com.vaadin.flow.data.provider.Query.class)},
            locals = {@APILocals(name = "orders", type = List.class),
                    @APILocals(name = "property", type = String.class),
                    @APILocals(name = "e", type = Exception.class)})
    private Stream<Resource> fetch(com.vaadin.flow.data.provider.Query<Resource, Void> query) {
        try {
            List<QuerySortOrder> orders = query.getSortOrders() == null ? List.of() : query.getSortOrders();
            // without a sort the newest are first
            String property = orders.isEmpty() ? "creation" : orders.getFirst().getSorted();
            boolean ascending = !orders.isEmpty() && orders.getFirst().getDirection() == SortDirection.ASCENDING;
            return resourceService.findPageForUser(query.getOffset(), query.getLimit(), property, ascending).stream();
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
            return (int) Math.min(Integer.MAX_VALUE, resourceService.countForUser());
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
            return 0;
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Component.class),
            parameters = {@APIParameter(name = "resource", type = Resource.class)},
            locals = {@APILocals(name = "link", type = ResourceService.ResourceLink.class),
                    @APILocals(name = "none", type = Span.class),
                    @APILocals(name = "missing", type = Span.class),
                    @APILocals(name = "e", type = Exception.class)})
    private Component linkOf(Resource resource) {
        try {
            ResourceLink link = resourceService.describeLink(resource);
            if (link == null) {
                Span none = new Span(loc.getValue(L.MSG_RESOURCE_NOT_LINKED));
                none.addClassName(CssStyles.TEXT_SECONDARY);
                return none;
            }
            if (link.present()) {
                return new Span(link.description());
            }
            // the object was deleted, the resource is probably not needed any more
            Span missing = new Span(String.format(loc.getValue(L.MSG_RESOURCE_LINK_MISSING), link.description()));
            missing.addClassName(CssStyles.TEXT_ERROR);
            return missing;
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
            return new Span();
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = Component.class),
            parameters = {@APIParameter(name = "resource", type = Resource.class)},
            locals = {@APILocals(name = "fileName", type = String.class),
                    @APILocals(name = "download", type = Anchor.class),
                    @APILocals(name = "downloadButton", type = Button.class),
                    @APILocals(name = "replaceButton", type = Button.class),
                    @APILocals(name = "deleteButton", type = Button.class),
                    @APILocals(name = "actions", type = HorizontalLayout.class)})
    private Component actionsOf(Resource resource) {
        String fileName = StringUtils.defaultIfBlank(resource.getOriginalName(), resource.getUuid())
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        Anchor download = new Anchor(new InputStreamDownloadHandler((InputStreamDownloadCallback) event -> {
            byte[] data;
            try {
                data = resourceService.getResourceData(resource);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
            return new DownloadResponse(new ByteArrayInputStream(data), fileName,
                    StringUtils.defaultIfBlank(resource.getMimeType(), "application/octet-stream"), data.length);
        }), "");
        download.getElement().setAttribute("download", true);
        Button downloadButton = new Button(Solid.DOWNLOAD.create());
        downloadButton.setThemeName("tertiary");
        UIUtils.addTooltip(downloadButton, loc.getValue(L.LABEL_DOWNLOAD));
        download.add(downloadButton);

        Button replaceButton = new Button(Solid.UPLOAD.create(), event -> openReplaceDialog(resource));
        replaceButton.setThemeName("tertiary");
        UIUtils.addTooltip(replaceButton, loc.getValue(L.LABEL_REPLACE_CONTENT));

        Button deleteButton = new Button(Solid.TRASH.create(), event ->
                ConfirmDialog.show(loc.getValue(L.MSG_CONFIRM_DELETE), () -> delete(List.of(resource))));
        deleteButton.setThemeName("error tertiary");
        UIUtils.addTooltip(deleteButton, loc.getValue(L.LABEL_DELETE));

        HorizontalLayout actions = new HorizontalLayout(download, replaceButton, deleteButton);
        actions.setSpacing(false);
        return actions;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            locals = {@APILocals(name = "selected", type = List.class)})
    private void deleteSelected() {
        List<Resource> selected = List.copyOf(grid.getSelectedItems());
        if (selected.isEmpty()) {
            return;
        }
        ConfirmDialog.show(String.format(loc.getValue(L.MSG_CONFIRM_DELETE_RESOURCES), selected.size()), () -> delete(selected));
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "resources", type = List.class)},
            locals = {@APILocals(name = "e", type = Exception.class)})
    private void delete(List<Resource> resources) {
        try {
            resourceService.softDelete(resources.stream().map(Resource::getUuid).toList());
            grid.deselectAll();
            refreshGrid();
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "resource", type = Resource.class)},
            locals = {@APILocals(name = "dialog", type = Dialog.class),
                    @APILocals(name = "handler", type = InMemoryUploadHandler.class),
                    @APILocals(name = "upload", type = Upload.class),
                    @APILocals(name = "content", type = VerticalLayout.class)})
    private void openReplaceDialog(Resource resource) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(loc.getValue(L.LABEL_REPLACE_CONTENT));
        dialog.setWidth("450px");

        boolean image = resource.getMimeType() != null && resource.getMimeType().startsWith("image/");
        InMemoryUploadHandler handler = new InMemoryUploadHandler((metadata, data) -> {
            try {
                resourceService.replace(resource, metadata.fileName(), data, metadata.contentType());
                dialog.close();
                Notification.success(loc.getValue(L.MSG_RESOURCE_REPLACED));
                refreshGrid();
            } catch (IllegalArgumentException e) {
                Notification.error(loc.getValue(L.ERROR_INVALID_IMAGE));
            } catch (Exception e) {
                UIUtils.internalServerError(loc, e);
            }
        });
        Upload upload = new Upload(handler);
        if (image) {
            upload.setAcceptedMimeTypes("image/png", "image/jpeg", "image/gif", "image/webp");
            upload.setMaxFileSize(ResourceService.MAX_IMAGE_BYTES);
        }
        upload.setUploadButton(new Button(loc.getValue(L.LABEL_REPLACE_CONTENT), Solid.UPLOAD.create()));

        VerticalLayout content = new VerticalLayout(upload);
        content.setPadding(false);
        dialog.add(content);
        dialog.getFooter().add(new Button(loc.getValue(L.LABEL_CLOSE), event -> dialog.close()));
        dialog.open();
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class))
    @Override
    public void refresh() throws Exception {
        refreshGrid();
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class))
    private void refreshGrid() {
        if (grid != null) {
            grid.getDataProvider().refreshAll();
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
