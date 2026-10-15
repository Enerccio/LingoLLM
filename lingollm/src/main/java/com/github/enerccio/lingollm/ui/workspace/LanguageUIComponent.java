package com.github.enerccio.lingollm.ui.workspace;

import com.vaadin.flow.component.Component;

/**
 * One tab of the UI of a language ({@link LanguageUIBase}): the level, the free chat, later the exercises.
 */
public interface LanguageUIComponent {

    /**
     * The localized name shown on the tab.
     */
    String getName();

    Component create();

    /**
     * Called when the language profile it belongs to was reloaded; the component shows the new data.
     */
    default void refresh() {
    }

    /**
     * Called when the tab becomes the one the user looks at.
     */
    void onEnter();

    /**
     * Called when the tab stops being the one the user looks at.
     */
    void onLeave();

}
