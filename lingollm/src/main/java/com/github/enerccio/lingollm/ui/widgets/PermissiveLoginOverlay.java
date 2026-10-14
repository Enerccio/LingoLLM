package com.github.enerccio.lingollm.ui.widgets;

import com.github.enerccio.lingollm.ui.JavascriptLibrary;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.login.LoginOverlay;


/**
 * This permissive variant allows empty password because vaadin is validating normally.
 * This is required for empty password logins which are checked in validator
 */
public class PermissiveLoginOverlay extends LoginOverlay {

    public PermissiveLoginOverlay() {
        disablePasswordValidation();
    }

    public PermissiveLoginOverlay(LoginI18n i18n) {
        super(i18n);
        disablePasswordValidation();
    }

    private void disablePasswordValidation() {
        JavascriptLibrary.disableLoginPasswordValidation(getElement());
    }
}
