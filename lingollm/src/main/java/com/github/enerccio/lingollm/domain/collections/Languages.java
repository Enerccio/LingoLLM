package com.github.enerccio.lingollm.domain.collections;

import com.github.enerccio.lingollm.ui.lang.LanguageDefaults;
import com.github.enerccio.lingollm.ui.lang.languages.German;
import com.github.enerccio.lingollm.ui.lang.languages.Japanese;
import com.github.enerccio.lingollm.ui.lang.LanguageIcons;
import com.github.enerccio.lingollm.ui.workspace.LanguageUI;
import com.github.enerccio.lingollm.ui.workspace.LanguageUI.LanguageConstructor;
import com.github.enerccio.lingollm.ui.workspace.Workspace;

/**
 * The languages a user can learn: each one has the flag, the defaults of a new language profile and the UI that is
 * shown for it in the workspace. Not to be confused with {@link com.github.enerccio.lingollm.loc.Language}, the
 * language of the interface.
 */
public enum Languages {

    JAPANESE(Japanese::new, LanguageIcons.JAPANESE, LanguageDefaults.JAPANESE),
    GERMAN(German::new, LanguageIcons.GERMAN, LanguageDefaults.GERMAN),

    ;

    private final LanguageConstructor constructor;
    public final String icon;
    public final LanguageDefaults defaults;

    Languages(LanguageConstructor constructor, String icon, LanguageDefaults defaults) {
        this.constructor = constructor;
        this.icon = icon;
        this.defaults = defaults;
    }

    public LanguageUI create(Workspace workspace) {
        return constructor.createLanguage(this, workspace);
    }
}
