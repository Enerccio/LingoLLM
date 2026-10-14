package com.github.enerccio.lingollm.bound;

import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.ui.components.ThreadCopyRequestAttributes;
import com.github.enerccio.lingollm.ui.workspace.Workspace;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import jakarta.servlet.http.HttpSession;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SessionInformation {
	// written on login (request thread), read by the session watcher and runForUsers threads
	public volatile UI mainUi;
	public volatile HttpSession boundSession;
	public volatile VaadinSession boundVaadinSession;
	public volatile User boundUser;
	public volatile ThreadCopyRequestAttributes accessAttributes;
	public volatile boolean initialized = false;
	public final Set<SessionCloseListener> sessionCloseListeners = ConcurrentHashMap.newKeySet();

	public UI getUI(Class<?> workspaceClazz, ApplicationPoint applicationPoint) {
		if (boundVaadinSession == null)
			return null;

		for (UI ui : boundVaadinSession.getUIs()) {
			if (applicationPoint.getUIs().containsKey(ui)) {
				Workspace workspace = applicationPoint.getUIs().get(ui);
				if (workspaceClazz.isAssignableFrom(workspace.getClass()))
					return ui;
			}
		}

		return null;
	}

	public Workspace getWorkspace(Class<?> workspaceClazz, ApplicationPoint applicationPoint) {
		UI ui = getUI(workspaceClazz, applicationPoint);

		if (ui != null)
			return applicationPoint.getUIs().get(ui);

		return null;
	}

}