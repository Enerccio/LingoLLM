package com.github.enerccio.lingollm.ui.widgets;

import com.github.enerccio.lingollm.CssStyles;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class ScrollPanel extends VerticalLayout {
    private VerticalLayout content;

    public ScrollPanel(){
        super();
        preparePanel();
    }

    public ScrollPanel(Component... children){
        super();
        preparePanel();
        this.add(children);
    }

    private void preparePanel() {
        addClassName(CssStyles.OVERFLOW_AUTO);
        content = new VerticalLayout();
        content.addClassName(CssStyles.DISPLAY_BLOCK);
        content.setWidth("100%");
        super.add(content);
        setHeight("100%");
    }

    public VerticalLayout getContent(){
        return content;
    }

    @Override
    public void add(Component... components){
        content.add(components);
    }

    @Override
    public void remove(Component... components){
        content.remove(components);
    }

    @Override
    public void removeAll(){
        content.removeAll();
    }

    @Override
    public void addComponentAsFirst(Component component) {
        content.addComponentAtIndex(0, component);
    }
}
