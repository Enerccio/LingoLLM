package com.github.enerccio.lingollm.ui.workspace;

import com.vaadin.flow.component.Component;

/**
 * The top-level layout shown after login. It hosts the parts (learning, settings, models, ...) and the
 * account actions (admin, change password, logout).
 */
public interface Workspace {

    Component create() throws Exception;

    /**
     * Refreshes all parts of the workspace.
     */
    void refresh() throws Exception;

    /**
     * Locks the navigation (other tabs, admin and logout) while the active part has unsaved changes.
     */
    void setNavigationLocked(boolean locked);

    boolean isNavigationLocked();

    Runnable getOnLogout();

    /**
     * Sets the logout action; the logout button is only shown when it is set before {@link #create()}.
     */
    void setOnLogout(Runnable onLogout);

}
