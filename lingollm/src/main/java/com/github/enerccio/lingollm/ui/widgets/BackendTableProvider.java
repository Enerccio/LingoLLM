package com.github.enerccio.lingollm.ui.widgets;

import com.vaadin.flow.data.provider.DataProvider;

import java.util.List;

public interface BackendTableProvider<TI extends BackendTableItem> extends DataProvider<TI, Void> {
    int size();
    void setIds(List<Long> ids);
    void remove(Long id);
    void setChecked(TI item, boolean value);
    void setChecked(Long id, boolean value);
    void setChecked(boolean value);
    List<Long> getCheckedIds();
    boolean isChecked(TI item);
}
