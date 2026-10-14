package com.github.enerccio.lingollm.ui.widgets;

import com.flowingcode.vaadin.addons.fontawesome.FontAwesome.Solid;
import com.github.enerccio.lingollm.CssStyles;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.textfield.TextField;

public class TextFieldPopOverComponent extends TextField {

    private final Icon infoIcon;
    private final Popover popover;

    public TextFieldPopOverComponent() {
        this(null);
    }

    public TextFieldPopOverComponent(String label) {
        super(label);

        infoIcon = Solid.INFO_CIRCLE.create();
        infoIcon.addClassName(CssStyles.INFO_ICON);

        popover = new Popover();
        popover.setTarget(infoIcon);

        setSuffixComponent(infoIcon);
    }

    public Icon getInfoIcon() {
        return infoIcon;
    }

    public Popover getPopover() {
        return popover;
    }

    public void setPopoverContent(Component content) {
        popover.removeAll();
        if (content != null) {
            popover.add(content);
        }
    }

    public void addPopoverContent(Component... components) {
        popover.add(components);
    }
}