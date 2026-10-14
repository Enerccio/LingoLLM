package com.github.enerccio.lingollm;

/**
 * Names of the CSS classes defined in {@code shared-styles.css}. Style components with these instead of
 * {@code getStyle().set(...)}, so the look lives in the stylesheet.
 */
public final class CssStyles {

    // --- Typography ---
    public static final String SECTION_TITLE = "section-title";
    public static final String TEXT_CENTER = "text-center";
    public static final String TEXT_BOLD = "text-bold";
    public static final String TEXT_S = "text-s";
    public static final String TEXT_MONOSPACE = "text-monospace";
    public static final String TEXT_MONOSPACE_LUMO = "text-monospace-lumo";
    public static final String TEXT_SECONDARY = "text-secondary";
    public static final String TEXT_ERROR = "text-error";
    public static final String HINT_TEXT = "hint-text";
    public static final String WHITE_SPACE_NORMAL = "white-space-normal";
    public static final String PRE_WRAP = "pre-wrap";
    public static final String WRAP_ANYWHERE = "wrap-anywhere";

    // --- Layout helpers ---
    public static final String FULL_WIDTH = "full-width";
    public static final String FULL_SIZE = "full-size";
    public static final String MIN_WIDTH_0 = "min-width-0";
    public static final String MIN_HEIGHT_0 = "min-height-0";
    public static final String MAX_WIDTH_FULL = "max-width-full";
    public static final String OVERFLOW_AUTO = "overflow-auto";
    public static final String DISPLAY_BLOCK = "display-block";
    public static final String FLEX_SHRINK_0 = "flex-shrink-0";
    public static final String GAP_S = "gap-s";
    public static final String FORM_PADDING_BOTTOM = "form-padding-bottom";
    public static final String PADDING_COMPACT = "padding-compact";

    // --- Panels and boxes ---
    public static final String NOTICE_WARNING = "notice-warning";
    public static final String INFO_ICON = "info-icon";

    // --- Images of resources ---
    public static final String MESSAGE_IMAGES = "message-images";
    public static final String MESSAGE_IMAGE = "message-image";

    // --- Language profiles ---
    public static final String LANGUAGE_FLAG = "language-flag";
    public static final String LANGUAGE_NOTE = "language-note";

    // --- Resizable text area toggle icon ---
    public static final String RESIZABLE_TEXT_AREA_ICON = "resizable-text-area-icon";

    private CssStyles() {
    }

}
