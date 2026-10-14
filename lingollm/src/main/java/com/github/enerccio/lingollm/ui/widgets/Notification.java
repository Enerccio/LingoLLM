package com.github.enerccio.lingollm.ui.widgets;

import com.flowingcode.vaadin.addons.fontawesome.FontAwesome.Solid;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import org.springframework.web.util.HtmlUtils;

public class Notification extends com.vaadin.flow.component.notification.Notification {
    public static final int DURATION_DEFAULT = 6000;
    public static final int DURATION_LONGER = 10000;
    public static final int DURATION_INFINITE = 0;

    public static void success(String message) {
        success(message, true);
    }

    public static void success(String message, int duration) {
        success(message, duration, true);
    }

    public static void success(String message, boolean htmlEscape) {
        success(message, DURATION_DEFAULT, htmlEscape);
    }

    public static void success(String message, int duration, boolean htmlEscape) {
        notify(message, duration, htmlEscape, NotificationVariant.LUMO_SUCCESS);
    }

    public static void error(String message) {
        error(message, DURATION_DEFAULT, true);
    }

    public static void error(String message, int duration) {
        error(message, duration, true);
    }

    public static void error(String message, boolean htmlEscape) {
        error(message, DURATION_DEFAULT, htmlEscape);
    }

    public static void error(String message, int duration, boolean htmlEscape) {
        notify(message, duration, htmlEscape, NotificationVariant.LUMO_ERROR);
    }

    public static void warning(String message) {
        warning(message, true);
    }

    public static void warning(String message, int duration) {
        warning(message, duration, true);
    }

    public static void warning(String message, boolean htmlEscape) {
        warning(message, DURATION_DEFAULT, htmlEscape);
    }

    public static void warning(String message, int duration, boolean htmlEscape) {
        notify(message, duration, htmlEscape, NotificationVariant.LUMO_CONTRAST);
    }

    private static void notify(String message, int duration, boolean htmlEscape, NotificationVariant notificationVariant) {
        com.vaadin.flow.component.notification.Notification notification = new com.vaadin.flow.component.notification.Notification();
        notification.addThemeVariants(notificationVariant);
        notification.setDuration(duration);
        notification.add(getMessage(message, notification, htmlEscape));
        notification.open();
    }

    private static Component getMessage(String message, com.vaadin.flow.component.notification.Notification notification, boolean htmlEscape) {
        if (htmlEscape)
            message = HtmlUtils.htmlEscape(message);

        message = message.replace("\n", "<br/>");

        HorizontalLayout layout = new HorizontalLayout();
        layout.add(new HtmlText(message));
        layout.add(new Button(Solid.CLOSE.create(),event -> notification.close()));
        layout.setAlignItems(FlexComponent.Alignment.CENTER);

        return layout;
    }
}
