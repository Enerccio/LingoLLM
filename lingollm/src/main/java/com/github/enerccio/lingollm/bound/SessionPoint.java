package com.github.enerccio.lingollm.bound;

import com.github.enerccio.lingollm.loc.Language;

/**
 * Session scoped state of the user's session.
 */
public class SessionPoint {

    private volatile Language language;

    /**
     * Language of the UI of this session, {@code null} until one is set (the default language is used then). Set from
     * the settings of the user when the workspace is opened.
     */
    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }

}
