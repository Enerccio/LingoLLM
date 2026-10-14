package com.github.enerccio.lingollm.ui.main;

import com.github.enerccio.lingollm.Configuration;
import com.github.enerccio.lingollm.bound.SessionManager;
import com.github.enerccio.lingollm.domain.security.PersistedLoginInfo;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.security.service.UserService;
import com.github.enerccio.lingollm.domain.traits.*;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.ui.dialogs.UserDialog;
import com.github.enerccio.lingollm.ui.widgets.PermissiveLoginOverlay;
import com.github.enerccio.lingollm.utils.UIUtils;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.login.LoginOverlay;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedSession;
import jakarta.servlet.http.Cookie;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.util.Arrays;
import java.util.List;

@Configurable(preConstruction = true)
@Extendable
public abstract class LoginCheckRoute extends Div implements ApplicationEntryPoint {
    private static final Logger log = LoggerFactory.getLogger(LoginCheckRoute.class);

    private static final String ID = "UIRoot";
    protected static final String PERSISTED_LOGIN_COOKIE_LOGIN = "_llllm_rememberMe_login";
    protected static final String PERSISTED_LOGIN_COOKIE_IDENTIFIER = "_llllm_rememberMe_identifier";
    protected static final String PERSISTED_LOGIN_COOKIE_SECRET = "_llllm_rememberMe_secret";
    protected static final String PERSISTED_LOGIN_COOKIE_WAS_SAVED = "_llllm_rememberMe_wasSaved";

    @Autowired
    protected Localization loc;

    @Autowired
    protected UserService userService;

    @Autowired
    private Configuration configuration;

    @Autowired
    protected SessionManager sessionManager;

    protected LoginOverlay loginOverlay;

    /**
     * Address the current request came from, for throttling failed logins.
     */
    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = String.class))
    protected String clientAddress() {
        return UIUtils.clientAddress(configuration);
    }

    protected abstract String getAppTitle();
    protected abstract String getAppDescription();
    protected abstract boolean authenticate(String userName, String password) throws Exception;
    protected abstract void proceedWithLogin(String username);

    public LoginCheckRoute() {
        setSizeFull();
        setId(ID);
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            locals = {@APILocals(name = "authUser", type = String.class),
                    @APILocals(name = "userDialog", type = UserDialog.class),
                    @APILocals(name = "e", type = Exception.class)})
    protected void showLogin() {
        try {
            if (userService.existsUsers()) {
                String authUser = authenticatedSessionUser();
                if (authUser == null) {
                    authUser = authenticateFromCookie();
                }
                if (authUser == null) {
                    login();
                } else {
                    performLogin(authUser);
                }
            } else {
                UserDialog userDialog = new UserDialog(false);
                userDialog.setFirstTime(true);
                userDialog.setOnSave(() -> {
                    UI.getCurrent().getPage().reload();
                });
                userDialog.create();
                userDialog.open();
                onAttach(new AttachEvent(UI.getCurrent(), false));
            }
        } catch (Exception e) {
            UIUtils.internalServerError(loc, e);
        }
    }

    /**
     * Login of the user that is already logged in this session (the page was reloaded), {@code null} when nobody is.
     */
    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = String.class))
    protected String authenticatedSessionUser() {
        return null;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = String.class),
            locals = {@APILocals(name = "cookieLogin", type = String.class),
                    @APILocals(name = "cookieIdentifier", type = String.class),
                    @APILocals(name = "cookieSecret", type = String.class),
                    @APILocals(name = "cookiePrefix", type = String.class),
                    @APILocals(name = "cookies", type = Cookie[].class),
                    @APILocals(name = "cookie", type = Cookie.class),
                    @APILocals(name = "user", type = User.class),
                    @APILocals(name = "loginInfo", type = PersistedLoginInfo.class),
                    @APILocals(name = "e", type = Exception.class)})
    protected String authenticateFromCookie() {
        String cookieLogin = null;
        String cookieIdentifier = null;
        String cookieSecret = null;
        String cookiePrefix = getCookiePrefix();
        Cookie[] cookies = VaadinService.getCurrentRequest().getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ((cookiePrefix + PERSISTED_LOGIN_COOKIE_LOGIN).equals(cookie.getName())) {
                    cookieLogin = cookie.getValue();
                }
                if ((cookiePrefix + PERSISTED_LOGIN_COOKIE_IDENTIFIER).equals(cookie.getName())) {
                    cookieIdentifier = cookie.getValue();
                }
                if ((cookiePrefix + PERSISTED_LOGIN_COOKIE_SECRET).equals(cookie.getName())) {
                    cookieSecret = cookie.getValue();
                }
            }
        }
        try {
            if (StringUtils.isAnyBlank(cookieLogin, cookieIdentifier, cookieSecret)) {
                deleteRememberMeCookies();
                return null;
            }

            User user = userService.findByName(cookieLogin);
            PersistedLoginInfo loginInfo = userService.authenticateFromCookie(user, cookieIdentifier, cookieSecret);
            if (loginInfo != null) {
                addRememberMeCookies(user, loginInfo);
                return user.getLogin();
            }

            deleteRememberMeCookies();
            return null;
        } catch (Exception e) {
            log.error(e.getMessage());
            log.debug(e.getMessage(), e);
            return null;
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "user", type = User.class),
                    @APIParameter(name = "persistedLoginInfo", type = PersistedLoginInfo.class)},
            locals = {@APILocals(name = "cookiePrefix", type = String.class),
                    @APILocals(name = "cookieLogin", type = Cookie.class),
                    @APILocals(name = "cookieIdentifier", type = Cookie.class),
                    @APILocals(name = "cookieSecret", type = Cookie.class),
                    @APILocals(name = "cookieSaved", type = Cookie.class),
                    @APILocals(name = "cookie", type = Cookie.class)})
    protected void addRememberMeCookies(User user, PersistedLoginInfo persistedLoginInfo) {
        long ttl = configuration.getPersistentLoginInfoTTL() == null ?
                60 * 60 * 24 * 30 : configuration.getPersistentLoginInfoTTL();
        String cookiePrefix = getCookiePrefix();
        Cookie cookieLogin = new Cookie(cookiePrefix + PERSISTED_LOGIN_COOKIE_LOGIN, user.getLogin());
        Cookie cookieIdentifier = new Cookie(cookiePrefix + PERSISTED_LOGIN_COOKIE_IDENTIFIER, persistedLoginInfo.getIdentifier());
        Cookie cookieSecret = new Cookie(cookiePrefix + PERSISTED_LOGIN_COOKIE_SECRET, persistedLoginInfo.getPlainSecret());
        Cookie cookieSaved = new Cookie(cookiePrefix + PERSISTED_LOGIN_COOKIE_WAS_SAVED, "yes");
        for (Cookie cookie : List.of(cookieLogin, cookieIdentifier, cookieSecret, cookieSaved)) {
            cookie.setPath("/");
            cookie.setMaxAge((int) ttl);
            cookie.setSecure(true);
            cookie.setHttpOnly(true);
            cookie.setAttribute("SameSite", "Lax");
            VaadinService.getCurrentResponse().addCookie(cookie);
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            locals = {@APILocals(name = "cookiePrefix", type = String.class),
                    @APILocals(name = "cookieLogin", type = Cookie.class),
                    @APILocals(name = "cookieIdentifier", type = Cookie.class),
                    @APILocals(name = "cookieSecret", type = Cookie.class),
                    @APILocals(name = "cookie", type = Cookie.class)})
    protected void deleteRememberMeCookies() {
        String cookiePrefix = getCookiePrefix();
        Cookie cookieLogin = new Cookie(cookiePrefix + PERSISTED_LOGIN_COOKIE_LOGIN, "");
        Cookie cookieIdentifier = new Cookie(cookiePrefix + PERSISTED_LOGIN_COOKIE_IDENTIFIER, "");
        Cookie cookieSecret = new Cookie(cookiePrefix + PERSISTED_LOGIN_COOKIE_SECRET, "");
        for (Cookie cookie : List.of(cookieLogin, cookieIdentifier, cookieSecret)) {
            cookie.setPath("/");
            cookie.setMaxAge(0);
            VaadinService.getCurrentResponse().addCookie(cookie);
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = String.class),
            locals = {@APILocals(name = "rawContext", type = String.class),
                    @APILocals(name = "context", type = String.class)})
    private String getCookiePrefix() {
        String rawContext = VaadinRequest.getCurrent().getContextPath();
        String context = (StringUtils.isBlank(rawContext) || "/".equals(rawContext)) ? "root" : rawContext.substring(1);
        return context + "_" + this.getClass().getSimpleName();
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            locals = {@APILocals(name = "saveLoginCheckBox", type = Checkbox.class)})
    private void login() {
        if (loginOverlay == null) {
            loginOverlay = new PermissiveLoginOverlay();
            loginOverlay.setTitle(getAppTitle());
            loginOverlay.setDescription(getAppDescription());
            loginOverlay.setI18n(getLoginI18n());
            loginOverlay.setForgotPasswordButtonVisible(showForgetPassword());

            Checkbox saveLoginCheckBox = new Checkbox(loc.getValue(L.LABEL_SAVE_LOGIN));
            saveLoginCheckBox.setWidth("100%");
            if (VaadinService.getCurrentRequest().getCookies() != null)
                saveLoginCheckBox.setValue(Arrays.stream(VaadinService.getCurrentRequest().getCookies()).anyMatch(cookie ->
                        cookie.getName().endsWith(this.getClass().getSimpleName() + PERSISTED_LOGIN_COOKIE_WAS_SAVED)));

            loginOverlay.getCustomFormArea().add(saveLoginCheckBox);

            loginOverlay.addLoginListener(event -> {
                String userName = event.getUsername();
                String password = event.getPassword();

                try {
                    boolean authenticated = authenticate(userName, password);

                    if (authenticated) {
                        if (canEnterApp(userName)) {
                            if (saveLoginCheckBox.getValue()) {
                                User user = userService.findByName(userName);
                                PersistedLoginInfo persistedLoginInfo = userService.generateNewPersistentInfo(user);
                                addRememberMeCookies(user, persistedLoginInfo);
                                userService.addPersistedLoginInfo(user, persistedLoginInfo);
                                userService.save(user);
                            } else {
                                String cookiePrefix = getCookiePrefix();
                                Cookie cookieSaved = new Cookie(cookiePrefix + PERSISTED_LOGIN_COOKIE_WAS_SAVED, "");
                                cookieSaved.setMaxAge(0);
                                cookieSaved.setPath("/");
                                VaadinService.getCurrentResponse().addCookie(cookieSaved);
                            }
                            loginOverlay.setOpened(false);
                            VaadinService.reinitializeSession(VaadinService.getCurrentRequest());
                            performLogin(userName);
                        } else {
                            loginOverlay.setEnabled(true);
                        }
                    } else {
                        loginOverlay.setError(true);
                        loginOverlay.setEnabled(true);
                    }
                } catch (Exception e) {
                    UIUtils.internalServerError(loc, e);
                }
            });
        }

        loginOverlay.setOpened(true);
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            parameters = {@APIParameter(name = "user", type = String.class)})
    private void performLogin(String user) {
        proceedWithLogin(user);

        onAttach(new AttachEvent(UI.getCurrent(), false));
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = boolean.class))
    protected boolean showForgetPassword() {
        return false;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = LoginI18n.class),
            locals = {@APILocals(name = "i18n", type = LoginI18n.class)})
    private LoginI18n getLoginI18n() {
        LoginI18n i18n = LoginI18n.createDefault();

        i18n.setHeader(new LoginI18n.Header());
        i18n.getHeader().setTitle(getAppTitle());
        i18n.getHeader().setDescription(getAppDescription());
        i18n.getForm().setUsername(loc.getValue(L.LABEL_USERNAME));
        i18n.getForm().setPassword(loc.getValue(L.LABEL_PASSWORD));
        i18n.getForm().setTitle(loc.getValue(L.LABEL_LOGIN));
        i18n.getForm().setSubmit(loc.getValue(L.LABEL_LOGIN));

        i18n.getForm().setForgotPassword(loc.getValue(L.LABEL_FORGOT_PASSWORD));
        i18n.getErrorMessage().setTitle(loc.getValue(L.LABEL_AUTHENTICATION_FAILED));
        i18n.getErrorMessage().setMessage(loc.getValue(L.MSG_AUTHENTICATION_FAILED_INFO));

        return i18n;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            locals = {@APILocals(name = "prefix", type = String.class),
                    @APILocals(name = "cookieSaved", type = Cookie.class)})
    public void logout() {
        deleteRememberMeCookies();

        String prefix = getCookiePrefix();
        Cookie cookieSaved = new Cookie(prefix + PERSISTED_LOGIN_COOKIE_WAS_SAVED, "");
        cookieSaved.setMaxAge(0);
        cookieSaved.setPath("/");
        VaadinService.getCurrentResponse().addCookie(cookieSaved);

        logoutWithoutCookieClear();
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            locals = {@APILocals(name = "currentUi", type = UI.class),
                    @APILocals(name = "rawContext", type = String.class),
                    @APILocals(name = "redirectPath", type = String.class),
                    @APILocals(name = "currentSession", type = VaadinSession.class),
                    @APILocals(name = "wrappedSession", type = WrappedSession.class)})
    public void logoutWithoutCookieClear() {
        UI currentUi = UI.getCurrent();
        if (currentUi != null) {
            String rawContext = VaadinRequest.getCurrent().getContextPath();
            String redirectPath = (rawContext == null || "/".equals(rawContext)) ? "/" : rawContext;

            currentUi.getPage().setLocation(redirectPath);
        }

        VaadinSession currentSession = VaadinSession.getCurrent();
        if (currentSession != null) {
            currentSession.close();
        }

        var wrappedSession = VaadinService.getCurrentRequest().getWrappedSession(false);
        if (wrappedSession != null) {
            wrappedSession.invalidate();
        }
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = boolean.class),
            parameters = {@APIParameter(name = "userName", type = String.class)})
    protected boolean canEnterApp(String userName) throws Exception {
        return true;
    }

    @PublicAPI(from = "1.0.0",
            returns = @APIReturn(type = void.class),
            locals = {@APILocals(name = "currentUi", type = UI.class),
                    @APILocals(name = "currentSession", type = VaadinSession.class)})
    public void exit() {
        UI currentUi = UI.getCurrent();
        if (currentUi != null) {
            currentUi.getPage().setLocation("about:blank");
        }

        VaadinSession currentSession = VaadinSession.getCurrent();
        if (currentSession != null) {
            currentSession.close();
        }
    }
}