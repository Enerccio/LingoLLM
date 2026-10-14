package com.github.enerccio.lingollm.ui.dialogs;

import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.vaadin.flow.component.ItemLabelGenerator;
import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@Configurable
public class ListSelectDialog<T> extends Dialog {

    @Autowired
    private Localization loc;

    private final ItemLabelGenerator<T> labelGenerator;
    private final Consumer<T> onSelect;
    private final boolean showCancel;
    private final String title;
    private final String label;

    private Button ok;
    private ComboBox<T> list;

    public ListSelectDialog(String title, String label, Consumer<T> onSelect) {
        this(title, label, Objects::toString, onSelect);
    }

    public ListSelectDialog(String title, String label, ItemLabelGenerator<T> labelGenerator, Consumer<T> onSelect) {
        this(title, label, labelGenerator, onSelect, true);
    }

    public ListSelectDialog(String title, String label, ItemLabelGenerator<T> labelGenerator, Consumer<T> onSelect, boolean showCancel) {
        this.title = title;
        this.label = label;
        this.labelGenerator = labelGenerator;
        this.onSelect = onSelect;
        this.showCancel = showCancel;
    }

    public void create() {
        setHeaderTitle(title);
        setWidth("450px");
        setHeight("200px");
        setModality(ModalityMode.STRICT);
        setCloseOnEsc(false);
        setCloseOnOutsideClick(false);

        HorizontalLayout hl = new HorizontalLayout();
        hl.setWidthFull();

        ok = new Button(loc.getValue(L.LABEL_OK), event -> {
            close();
            onSelect.accept(list.getValue());
        });
        ok.setEnabled(false);
        hl.add(ok);

        if (showCancel) {
            Button cancel = new Button(loc.getValue(L.LABEL_CANCEL), event -> close());
            hl.add(cancel);
            hl.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        }

        list = new ComboBox<>(label);
        list.addValueChangeListener(event -> {
            if (list.getValue() == null) {
                ok.setEnabled(false);
            } else {
                ok.setEnabled(true);
            }
        });
        list.setItemLabelGenerator(labelGenerator);
        list.setWidth("100%");

        VerticalLayout vl = new VerticalLayout();
        vl.add(list);
        vl.add(hl);
        vl.setHorizontalComponentAlignment(FlexComponent.Alignment.CENTER, list);
        vl.setHorizontalComponentAlignment(FlexComponent.Alignment.CENTER, hl);

        add(vl);
    }

    public void setItems(List<T> items) {
        list.setItems(items);
    }

}
