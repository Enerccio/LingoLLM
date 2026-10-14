package com.github.enerccio.lingollm.ui.workspace;

import com.vaadin.flow.component.Component;

public interface WorkspaceComponent {

    Component create() throws Exception;
    void refresh() throws Exception;
    void onTabSwitched() throws Exception;
    void onTabClosed() throws Exception;

}
