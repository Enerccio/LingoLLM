package com.github.enerccio.lingollm.bound;

import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.ui.workspace.Workspace;
import com.vaadin.flow.component.UI;

import java.util.concurrent.ConcurrentHashMap;

public class ApplicationPoint {

    protected final ConcurrentHashMap<UI, Workspace> uis;
    protected final ConcurrentHashMap<UI, User> uiUsers;

    public ApplicationPoint() {
        uis = new ConcurrentHashMap<>();
        uiUsers = new ConcurrentHashMap<>();
    }

    /**
     * Registers the UI with its workspace (may be null) and user; the entries are removed when the UI is detached,
     * otherwise the closed UIs would stay referenced forever.
     */
    public void register(UI ui, Workspace workspace, User user) {
        if (workspace != null)
            uis.put(ui, workspace);
        uiUsers.put(ui, user);
        ui.addDetachListener(_ -> {
            uis.remove(ui);
            uiUsers.remove(ui);
        });
    }

    public ConcurrentHashMap<UI, Workspace> getUIs() {
        return uis;
    }

    public ConcurrentHashMap<UI, User> getUiUsers() {
        return uiUsers;
    }
}
