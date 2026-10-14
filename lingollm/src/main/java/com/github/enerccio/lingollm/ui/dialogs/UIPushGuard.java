package com.github.enerccio.lingollm.ui.dialogs;

import com.vaadin.flow.component.UI;

import java.util.HashSet;
import java.util.Set;

public final class UIPushGuard {

    private static final ThreadLocal<Set<UI>> currentlyPushingUis = ThreadLocal.withInitial(HashSet::new);

    private UIPushGuard() {

    }

    public static void push(UI ui) {
        if (!currentlyPushingUis.get().contains(ui)) {
            currentlyPushingUis.get().add(ui);
            try {
                ui.push();
            } finally {
                currentlyPushingUis.get().remove(ui);
            }
        }
    }

}
