package com.github.enerccio.lingollm.bound;

import com.github.enerccio.lingollm.Constants;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.ui.components.ThreadCopyRequestAttributes;
import com.github.enerccio.lingollm.ui.components.ThreadCopyRequestAttributes.InRequestScope;
import com.github.enerccio.lingollm.utils.ThreadUtils;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedHttpSession;
import com.vaadin.flow.server.WrappedSession;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Tracks every HTTP session of the application (fed by {@link SessionTrackingListener}), binds the logged-in user
 * to it and closes UIs (and with them the session) that stopped sending heartbeats.
 */
public class SessionManager extends Thread implements InitializingBean, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);
    private static final Predicate<User> MATCH_ALL = (user) -> true;
    private static final Predicate<SessionInformation> FILTER_NO_USERS = (sinfo) -> sinfo.boundUser != null;
    private static final ThreadLocal<VaadinSession> ACTIVE_SESSION = new ThreadLocal<>();

    private final ConcurrentHashMap<String, SessionInformation> activeSessions = new ConcurrentHashMap<>();
    private long timeout = 1000L * Constants.DEAD_SESSION_CHECK_TIMEOUT;

    public SessionManager() {
        setDaemon(true);
        setName("Inactive session watcher");
    }

    public void onSessionCreate(HttpSession session) {
        activeSessions.computeIfAbsent(session.getId(), _ -> {
            SessionInformation sinfo = new SessionInformation();
            sinfo.boundSession = session;
            return sinfo;
        });
    }

    public void onSessionDestroy(HttpSession session) {
        SessionInformation sessionInformation = activeSessions.remove(session.getId());
        if (sessionInformation == null)
            return;

        for (SessionInformation otherSession : new ArrayList<>(activeSessions.values())) {
            VaadinSession otherVaadinSession = otherSession.boundVaadinSession;
            if (otherVaadinSession == null)
                continue;

            for (SessionCloseListener listener : new ArrayList<>(otherSession.sessionCloseListeners)) {
                try {
                    // access() may run the command later and on another thread, so the request scope is set inside
                    otherVaadinSession.access(() -> {
                        try (InRequestScope _ = new InRequestScope(otherSession.accessAttributes)) {
                            listener.onSessionClose(sessionInformation);
                        }
                    });
                } catch (Throwable e) {
                    log.error(e.getMessage());
                    log.debug(e.getMessage(), e);
                }
            }
        }
    }

    public void userLoggedIn(User user, VaadinSession session) {
        WrappedSession wrappedSession = session.getSession();
        String sessionId = wrappedSession.getId();
        SessionInformation sinfo = activeSessions.get(sessionId);

        if (sinfo == null && wrappedSession instanceof WrappedHttpSession whs) {
            // session was created before the listener was registered, track it now
            log.warn("Missing session information for user {}, sessionId={}, registering", user.getLogin(), sessionId);
            onSessionCreate(whs.getHttpSession());
            sinfo = activeSessions.get(sessionId);
        }

        if (sinfo == null) {
            log.error("MISSING SESSION INFORMATION FOR USER {}, sessionId={}", user.getLogin(), sessionId);
            return;
        }

        for (UI ui : session.getUIs())
            ui.getInternals().setLastHeartbeatTimestamp(System.currentTimeMillis());

        synchronized (sinfo) {
            if (!sinfo.initialized) {
                sinfo.mainUi = UI.getCurrent();
                sinfo.boundVaadinSession = session;
                sinfo.accessAttributes = ThreadCopyRequestAttributes.create();
                sinfo.boundSession.setAttribute("userName", user.getLogin());
                // the session scoped proxy can't be used outside of its session, and boundUser is read by other threads
                User snapshot = new User();
                snapshot.setId(user.getId());
                snapshot.setLogin(user.getLogin());
                snapshot.setFullName(user.getFullName());
                snapshot.setAdmin(user.isAdmin());
                sinfo.boundUser = snapshot;
                sinfo.initialized = true;
            }
        }
    }

    public List<SessionInformation> getOpenedSessions(User user) {
        List<SessionInformation> sessionInformations = new ArrayList<>();
        for (SessionInformation sessionInformation : activeSessions.values()) {
            User boundUser = sessionInformation.boundUser;
            if (boundUser != null && boundUser.getId().equals(user.getId())) {
                sessionInformations.add(sessionInformation);
            }
        }
        return sessionInformations;
    }

    public void addSessionCloseListener(SessionCloseListener listener) {
        String sessionId = VaadinSession.getCurrent().getSession().getId();
        SessionInformation sinfo = activeSessions.get(sessionId);

        if (sinfo != null) {
            sinfo.sessionCloseListeners.add(listener);
        }
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            try {
                //noinspection BusyWait
                Thread.sleep(Math.max(1000L, timeout / 10));
            } catch (InterruptedException e) {
                return;
            }

            for (SessionInformation sinfo : activeSessions.values()) {
                try {
                    checkSession(sinfo);
                } catch (Throwable e) {
                    // never let a single broken session kill the watcher
                    log.error(e.getMessage());
                    log.debug(e.getMessage(), e);
                }
            }
        }
    }

    /**
     * UI detach detection based on session; once every UI of the session is closed, the session is invalidated.
     */
    private void checkSession(SessionInformation sinfo) {
        VaadinSession session = sinfo.boundVaadinSession;
        if (session == null)
            return;

        Lock lock = session.getLockInstance();
        // a session that is locked right now is being used by someone, so it is not dead
        if (lock == null || !lock.tryLock())
            return;

        boolean invalidate = false;
        try {
            for (UI ui : new ArrayList<>(session.getUIs())) {
                if (!isUIActive(ui, lock)) {
                    ui.accessSynchronously(ui::close);
                }
            }

            invalidate = session.getUIs().stream().allMatch(UI::isClosing);
        } finally {
            session.unlock();
        }

        if (invalidate) {
            try (InRequestScope _ = new InRequestScope(sinfo.accessAttributes)) {
                sinfo.boundSession.invalidate();
            } catch (IllegalStateException e) {
                // already invalidated, but the destroy event did not reach us
                activeSessions.values().remove(sinfo);
            }
        }
    }

    private boolean isUIActive(UI ui, Lock sessionLock) {
        if (ui.isClosing()) {
            return false;
        }

        // somebody (request thread or background task) is waiting for the session, it is alive
        if (sessionLock instanceof ReentrantLock rl && rl.hasQueuedThreads()) {
            ui.getInternals().setLastHeartbeatTimestamp(System.currentTimeMillis());
            return true;
        }

        long timeout = getHeartbeatTimeout(ui);
        return timeout < 0 || System.currentTimeMillis() - ui.getInternals().getLastHeartbeatTimestamp() < timeout;
    }

    /**
     * Heartbeat timeout for the UI, never shorter than three heartbeat intervals, otherwise idle (but open) UIs
     * would be closed between two heartbeats.
     */
    private long getHeartbeatTimeout(UI ui) {
        long timeout = getHeartbeatTimeout();
        if (timeout < 0)
            return timeout;
        int heartbeatInterval = ui.getSession().getConfiguration().getHeartbeatInterval();
        if (heartbeatInterval > 0)
            timeout = Math.max(timeout, 3L * 1000 * heartbeatInterval);
        return timeout;
    }

    public long getHeartbeatTimeout() {
        return timeout;
    }

    public List<User> getActiveUsers() {
        return activeSessions.values().stream().filter(FILTER_NO_USERS).map(sinfo -> sinfo.boundUser).collect(Collectors.toList());
    }

    public void runForUsers(RunInSession runnable, boolean uiBound) {
        runForUsers(MATCH_ALL, runnable, uiBound);
    }

    /**
     * Runs the runnable for every session of a logged-in user matching the test. When uiBound, the runnable is
     * executed asynchronously with the session (and one of its UIs) locked.
     */
    public void runForUsers(Predicate<User> test, RunInSession runnable, boolean uiBound) {
        runForUsers(test, runnable, uiBound, false);
    }

    /**
     * As {@link #runForUsers(Predicate, RunInSession, boolean)}, with skipCurrent the caller's own session is left out.
     */
    public void runForUsers(Predicate<User> test, RunInSession runnable, boolean uiBound, boolean skipCurrent) {
        Predicate<SessionInformation> sessionInfoTest = (sinfo) -> test.test(sinfo.boundUser);
        VaadinSession vaadinSession = VaadinSession.getCurrent();
        ThreadUtils.executeInThread(() -> {
            activeSessions.values().parallelStream().filter(FILTER_NO_USERS).filter(sessionInfoTest).forEach(sinfo -> {
                try {
                    // no Vaadin session outside the UI thread, then there is nothing to skip
                    if (skipCurrent && vaadinSession != null && vaadinSession == sinfo.boundVaadinSession)
                        return;
                    if (uiBound) {
                        sinfo.boundVaadinSession.access(() -> {
                            try (InRequestScope _ = new InRequestScope(sinfo.accessAttributes)) {
                                UI ui = getLiveUI(sinfo);
                                if (ui != null) {
                                    ui.access(() -> {
                                        try (InRequestScope _ = new InRequestScope(sinfo.accessAttributes)) {
                                            runnable.run(sinfo);
                                        }
                                    });
                                } else {
                                    runnable.run(sinfo);
                                }
                            }
                        });
                    } else {
                        try (InRequestScope _ = new InRequestScope(sinfo.accessAttributes)) {
                            runnable.run(sinfo);
                        }
                    }
                } catch (Throwable e) {
                    log.error(e.getMessage());
                    log.debug(e.getMessage(), e);
                }
            });
        });
    }

    /**
     * Main UI of the session if still alive, otherwise any other alive UI of the session.
     */
    private UI getLiveUI(SessionInformation sinfo) {
        UI mainUi = sinfo.mainUi;
        if (mainUi != null && !mainUi.isClosing() && mainUi.getSession() == sinfo.boundVaadinSession)
            return mainUi;
        for (UI ui : sinfo.boundVaadinSession.getUIs()) {
            if (!ui.isClosing())
                return ui;
        }
        return null;
    }

    public SessionInformation getSessionInformation(String sessionId) {
        return activeSessions.get(sessionId);
    }

    public Set<String> getActiveSessionIds() {
        return Collections.unmodifiableSet(activeSessions.keySet());
    }

    @Override
    public void destroy() throws Exception {
        interrupt();
        join(timeout);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        start();
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public static boolean isLocalCall() {
        return VaadinSession.getCurrent() == ACTIVE_SESSION.get();
    }

    public static void onSessionEnter() {
        ACTIVE_SESSION.set(VaadinSession.getCurrent());
    }

    public static void onSessionExit() {
        ACTIVE_SESSION.remove();
    }
}
