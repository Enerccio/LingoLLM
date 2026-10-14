package com.github.enerccio.lingollm.ui.widgets;

import com.flowingcode.vaadin.addons.fontawesome.FontAwesome.Solid;
import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.textfield.TextArea;

/**
 * Gives a {@link TextArea} two sizing modes: fixed height with an internal scrollbar (default) and growing to fit the
 * whole content. A small icon floating in the bottom right corner of the field switches between them.
 */
public final class ResizableTextArea {

    private static final String SLOT_ATTRIBUTE = "slot";

    private ResizableTextArea() {
    }

    /**
     * Makes the text area a fixed height one with the toggle icon.
     *
     * @param loc         localization for the toggle tooltip
     * @param area        text area to modify
     * @param fixedHeight css height used in the fixed mode, in expanded mode it is the minimal height
     */
    public static void install(Localization loc, TextArea area, String fixedHeight) {
        Icon expand = createToggleIcon(Solid.EXPAND_ALT, loc.getValue(L.LABEL_FIT_TEXT_AREA_TO_CONTENT));
        Icon collapse = createToggleIcon(Solid.COMPRESS_ALT, loc.getValue(L.LABEL_FIXED_TEXT_AREA_HEIGHT));
        collapse.setVisible(false);
        applyMode(area, fixedHeight, false);

        expand.addClickListener(e -> {
            applyMode(area, fixedHeight, true);
            expand.setVisible(false);
            collapse.setVisible(true);
        });
        collapse.addClickListener(e -> {
            applyMode(area, fixedHeight, false);
            collapse.setVisible(false);
            expand.setVisible(true);
        });

        // append (not setSuffixComponent) so a suffix already present, like the info icon, is kept
        area.getElement().appendChild(expand.getElement(), collapse.getElement());
    }

    private static Icon createToggleIcon(Solid solid, String tooltip) {
        Icon icon = solid.create();
        icon.setSize("var(--lumo-font-size-xs)");
        icon.getElement().setAttribute("title", tooltip);
        // the field's inner container scrolls the text and the icon is a child of it, so it is pinned to the visible
        // bottom right corner by shifting it with the scroll position the text area publishes as a CSS variable
        // (position: sticky does not work here, it stays inside the unscrolled height of the container).
        // It is pulled over the text instead of taking space from it.
        icon.addClassName(CssStyles.RESIZABLE_TEXT_AREA_ICON);
        icon.getElement().setAttribute(SLOT_ATTRIBUTE, "suffix");
        return icon;
    }

    private static void applyMode(TextArea area, String fixedHeight, boolean expanded) {
        if (expanded) {
            area.setHeight(null);
            area.setMinHeight(fixedHeight);
        } else {
            area.setMinHeight(null);
            area.setHeight(fixedHeight);
        }
    }
}
