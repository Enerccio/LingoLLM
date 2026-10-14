package com.github.enerccio.lingollm.loc;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Base of a bundle: {@link #loadMessages()} fills the texts with {@link #setValue}.
 */
public abstract class LocalizationBundleBase implements LocalizationBundle {

    private final Map<L, String> messages = new EnumMap<>(L.class);

    protected LocalizationBundleBase() {
        loadMessages();
    }

    protected abstract void loadMessages();

    protected void setValue(L l, String text) {
        messages.put(l, text);
    }

    @Override
    public String getValue(L l) {
        return messages.get(l);
    }

    @Override
    public Map<L, String> getMessages() {
        return Collections.unmodifiableMap(messages);
    }

}
