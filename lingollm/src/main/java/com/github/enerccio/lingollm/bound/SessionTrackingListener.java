package com.github.enerccio.lingollm.bound;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Forwards servlet session lifecycle to the {@link SessionManager} bean, without it no session is ever tracked.
 */
public class SessionTrackingListener implements HttpSessionListener {

    private final ConfigurableApplicationContext context;

    public SessionTrackingListener(ConfigurableApplicationContext context) {
        this.context = context;
    }

    @Override
    public void sessionCreated(HttpSessionEvent se) {
        SessionManager sessionManager = getSessionManager();
        if (sessionManager != null)
            sessionManager.onSessionCreate(se.getSession());
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        SessionManager sessionManager = getSessionManager();
        if (sessionManager != null)
            sessionManager.onSessionDestroy(se.getSession());
    }

    private SessionManager getSessionManager() {
        if (!context.isActive())
            return null;
        return context.getBean(SessionManager.class);
    }
}
