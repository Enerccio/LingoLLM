package com.github.enerccio.lingollm.utils;

import com.github.enerccio.lingollm.Configuration;
import com.github.enerccio.lingollm.UIConstants;
import com.github.enerccio.lingollm.domain.service.InferenceErrors;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.ui.JavascriptLibrary;
import com.github.enerccio.lingollm.ui.dialogs.ErrorDialog;
import com.github.enerccio.lingollm.ui.widgets.Notification;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ItemLabelGenerator;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.data.binder.ErrorLevel;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinService;
import jakarta.servlet.http.Cookie;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vaadin.firitin.layouts.VTabSheet;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

public class UIUtils {
    private static final Logger log = LoggerFactory.getLogger(UIUtils.class);

    private static final ThreadLocal<SimpleDateFormat> simpleDateFormat = ThreadLocal.withInitial(() -> new SimpleDateFormat("dd.MM.yyyy HH:mm:ss"));

    /**
     * Address of the client of the current request, null outside of one. {@code X-Forwarded-For} is only read when the
     * connection comes from one of the configured trusted proxies, see {@link ClientAddressResolver}.
     */
    public static String clientAddress(Configuration configuration) {
        VaadinRequest request = VaadinService.getCurrentRequest();
        if (request == null) {
            return null;
        }
        return configuration.getClientAddressResolver().resolve(request.getRemoteAddr(), request.getHeader("X-Forwarded-For"));
    }

    public static void showError(String errorMessage, Throwable cause) {
        log.error(errorMessage, cause);

        ErrorDialog errorDialog = new ErrorDialog(errorMessage, cause);

        errorDialog.open(true);
    }

    /**
     * Failure of a request to an inference provider: a notification with what to fix, an error dialog when it is
     * not a provider failure.
     */
    public static void inferenceError(Localization loc, Throwable cause) {
        String description = InferenceErrors.describe(loc, cause);
        if (description == null) {
            internalServerError(loc, cause);
            return;
        }
        log.warn("Inference request failed: {}", cause.getMessage());
        Notification.error(description, Notification.DURATION_LONGER);
    }

    public static void internalServerError(Localization loc, Throwable cause) {
        log.error(loc.getValue(L.ERROR_INTERNAL_SERVER_ERROR), cause);

        ErrorDialog errorDialog = new ErrorDialog(loc.getValue(L.ERROR_INTERNAL_SERVER_ERROR), cause);

        errorDialog.open(true);
    }

    /**
     * Runs a change of a component that may belong to another user's session (e.g. from an extension unload) while
     * that session is locked, through {@code ui.access(...)}; directly when the component is not attached to a UI.
     * A UI that was detached in the meantime (closed tab, expired session) is skipped: the component is gone with it.
     */
    public static void accessComponent(Component component, Runnable change) {
        component.getUI().ifPresentOrElse(ui -> {
            try {
                ui.access(() -> {
                    try {
                        change.run();
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                });
            } catch (UIDetachedException e) {
                log.debug("UI already detached, skipping component change");
            }
        }, change);
    }

    public static Component voidComponent() {
        return new Span("");
    }

    public static Component voidComponent(String height, String width) {
        Span label = new Span("");

        if (height != null)
            label.setHeight(height);

        if (width != null)
            label.setWidth(width);

        return label;
    }

    public static void addTabDelimiter(VTabSheet tabs) {
        Tab delim = tabs.addTab("", voidComponent());
        delim.setEnabled(false);
        delim.add(VaadinIcon.LINE_V.create());
    }

    public static void addTooltip(Component component, String text) {
        component.getElement().setAttribute("title", text);
    }

    public static void setColumnHeader(Grid.Column<?> column, String text, Alignment alignment) {
        Span label = new Span(text);

        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setSpacing(false);
        layout.setMargin(false);
        layout.setPadding(false);
        label.add(label);
        layout.setHorizontalComponentAlignment(alignment, label);

        column.setHeader(layout);
    }

    public static void setColumnHeader(Grid.Column<?> column, Icon icon, Alignment alignment) {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setSpacing(false);
        layout.setMargin(false);
        layout.setPadding(false);
        layout.add(icon);
        layout.setHorizontalComponentAlignment(alignment, icon);

        column.setHeader(layout);
    }

    public static Icon createTableIcon(VaadinIcon vaadinIcon) {
        Icon icon = vaadinIcon.create();
        icon.setSize("16px");

        return icon;
    }

    public static String validationToMessage(ValidationException ve) {
        List<ValidationResult> errors = ve.getValidationErrors();
        List<String> messages = new ArrayList<>();
        for (ValidationResult vr : errors) {
            String message = vr.getErrorMessage();
            messages.add(message);
        }
        return String.join(", ", messages);
    }

    public static void showValidationErrors(Localization loc, ValidationException ve) {
        String msg = UIUtils.validationToMessage(ve);
        if (StringUtils.isBlank(msg))
            Notification.warning(loc.getValue(L.MSG_VALIDATION_FAILED_CANT_SAVE));
        else
            Notification.warning(loc.getValue(L.MSG_VALIDATION_FAILED_CANT_SAVE_EXT) + msg);
    }

    public static ValidationException simpleValidationError(String error) {
        return new ValidationException(Collections.emptyList(),
                Collections.singletonList(new ValidationResult() {
                    @Override
                    public String getErrorMessage() {
                        return error;
                    }

                    @Override
                    public Optional<ErrorLevel> getErrorLevel() {
                        return Optional.empty();
                    }
                }));
    }

    public static VerticalLayout createEmptyVerticalLayout() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(false);
        layout.setMargin(false);
        layout.setPadding(false);

        return layout;
    }

    public static HorizontalLayout createEmptyHorizontalLayout() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setSpacing(false);
        layout.setMargin(false);
        layout.setPadding(false);

        return layout;
    }

    public static String convertDoubleToHuman(Double number) {
        if (number == null)
            return null;

        BigDecimal decimal = new BigDecimal(number);
        decimal = decimal.setScale(2, BigDecimal.ROUND_HALF_DOWN);
        return decimal.toPlainString();
    }

    public static String formatNumber(Number num, Locale locale) {
        if (num == null)
            return null;

        NumberFormat numberFormat = NumberFormat.getNumberInstance(locale);
        return numberFormat.format(num);
    }

    public static Component centerComponent(Component component) {
        HorizontalLayout layout = createEmptyHorizontalLayout();
        layout.add(component);
        layout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        return layout;
    }

    public static void getPositionAndSizeOfElement(Element element, PositionSizeCallback callback) {
        JavascriptLibrary.getPositionAndSize(element).then(data -> {
            String serializedData = data.asString();
            String[] items = serializedData.split(Pattern.quote(";"));

            PositionSizeInformation information = new PositionSizeInformation();
            information.x = Double.parseDouble(items[0]);
            information.y = Double.parseDouble(items[1]);
            information.w = Double.parseDouble(items[2]);
            information.h = Double.parseDouble(items[3]);

            callback.callback(information);
        });
    }

    public static int parsePositionFromPixels(String position) {
        if (position.endsWith("px")) {
            return Integer.parseInt(position.substring(0, position.lastIndexOf("px")).trim());
        }
        return -1;
    }

    /**
     * Repositions dialog to be offset off from other dialog depending on if it will overflow or not.
     * @param dialog
     * @param x
     * @param y
     * @param widthInfo
     * @param heightInfo
     * @return true if it could be done, false if not (width, height -1 etc)
     */
    public static boolean repositionDialog(Dialog dialog, int x, int y, int widthInfo, int heightInfo) {
        if (widthInfo <= 0 || heightInfo <= 0)
            return false;

        UI ui = UI.getCurrent();
        ui.getPage().retrieveExtendedClientDetails(extendedClientDetails -> {
            boolean invertLeft = (x + widthInfo + UIConstants.DIALOG_POSITION_OFFSET_PX) > extendedClientDetails.getBodyClientWidth();
            boolean invertTop = (y + heightInfo + UIConstants.DIALOG_POSITION_OFFSET_PX) > extendedClientDetails.getBodyClientHeight();
            int leftPos = invertLeft ? x - UIConstants.DIALOG_POSITION_OFFSET_PX : x + UIConstants.DIALOG_POSITION_OFFSET_PX;
            if (leftPos >= 0)
                dialog.setLeft(leftPos + "px");
            int topPos = invertTop ? y - UIConstants.DIALOG_POSITION_OFFSET_PX : y + UIConstants.DIALOG_POSITION_OFFSET_PX;
            if (topPos >= 0)
                dialog.setTop(topPos + "px");
        });
        dialog.setWidth(widthInfo + "px");
        dialog.setHeight(heightInfo + "px");
        return true;
    }

    /**
     * If LUMO theme is used and you want East anchor, add +25 to offsetY, see more {@link Dialog#setLeft(String)}
     * @param dialog
     * @param anchor
     * @param offsetX
     * @param offsetY
     * @param width
     * @param height
     */
    public static void moveDialogToEdge(Dialog dialog, Anchor anchor, int offsetX, int offsetY, int width, int height) {
        if (anchor == Anchor.NorthWest) {
            dialog.setLeft(offsetX + "px");
            dialog.setTop(offsetY + "px");
        } else {
            UI.getCurrent().getPage().retrieveExtendedClientDetails(extendedClientDetails -> {
                switch (anchor) {
                    case NorthEast -> {
                        dialog.setLeft((extendedClientDetails.getBodyClientWidth() - (width + offsetX)) + "px");
                        dialog.setTop(offsetY + "px");
                    }
                    case SouthWest -> {
                        dialog.setLeft(offsetX + "px");
                        dialog.setTop((extendedClientDetails.getBodyClientHeight() - (offsetY + height)) + "px");
                    }
                    case SouthEast -> {
                        dialog.setLeft((extendedClientDetails.getBodyClientWidth() - (width + offsetX)) + "px");
                        dialog.setTop((extendedClientDetails.getBodyClientHeight() - (offsetY + height)) + "px");
                    }
                }
            });
        }
    }

    public static void setCookieValue(String name, String value) {
        setCookieValue(name, value, Integer.MAX_VALUE);
    }

    public static <T> ItemLabelGenerator<T> wrapNull(Function<T, String> function) {
        return wrapNull(function, "");
    }

    public static <T> ItemLabelGenerator<T> wrapNull(Function<T, String> function,
                                                     String defaultValue) {
        if (defaultValue == null) throw new NullPointerException();
        return v -> {
            String text = function.apply(v);
            return Objects.toString(text, defaultValue);
        };
    }

    public static <T> ItemLabelGenerator<T> selectFirstNonNull(Function<T, String> function, Function<T, String> function2) {
        return v -> {
            if (v == null) {
                return "";
            }

            String text = function.apply(v);
            if (text == null) {
                text = function2.apply(v);
            }
            return Objects.toString(text, "");
        };
    }

    public static void setCookieValue(String name, String value, int maxAge) {
        Cookie cookie = getCookie(name);

        if (cookie == null)
            cookie = new Cookie(name, value);
        else
            cookie.setValue(value);

        cookie.setPath(VaadinService.getCurrentRequest().getContextPath());
        cookie.setMaxAge(maxAge);
        VaadinService.getCurrentResponse().addCookie(cookie);
    }

    public static String getCookieValue(String name) {
        Cookie cookie = getCookie(name);

        return cookie == null ? null : cookie.getValue();
    }

    public static Cookie getCookie(String name) {
        Cookie[] cookies = VaadinService.getCurrentRequest().getCookies();

        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie;
            }
        }

        return null;
    }

    public static void setupComboBoxOverlayWidth(ComboBox<?> comboBox, List<String> items) {
        int max = 0;
        for (String item : items)
            if (item.length() > max)
                max = item.length();

        String width = (max * 8) + "px";

        comboBox.getStyle().set("--vaadin-combo-box-overlay-width", width);
    }

    public static Component menuItemWithIcon(Icon icon, String text) {
        Span span = new Span(text);
        span.setWidthFull();
        HorizontalLayout horizontalLayout = new HorizontalLayout(icon, span);
        horizontalLayout.setWidthFull();
        horizontalLayout.expand(span);
        return horizontalLayout;
    }

    public enum Anchor {
        NorthEast, NorthWest, SouthWest, SouthEast
    }

    public interface PositionSizeCallback {

        void callback(PositionSizeInformation information);

    }

    public static class PositionSizeInformation {

        public double x, y, w, h;

    }
}
