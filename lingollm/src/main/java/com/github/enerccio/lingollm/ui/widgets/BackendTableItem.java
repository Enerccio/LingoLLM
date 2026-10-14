package com.github.enerccio.lingollm.ui.widgets;

import java.util.Objects;

public class BackendTableItem {
    protected final Long id;

    public BackendTableItem() {
        id = null;
    }

    public BackendTableItem(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BackendTableItem that = (BackendTableItem) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
