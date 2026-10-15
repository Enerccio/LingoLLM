package com.github.enerccio.lingollm.ui.workspace;

import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

/**
 * The free chats of the language profile. For now only the tab: the chat list and the chat itself come later (see
 * {@code TODO.md}).
 */
@Configurable
public class FreeChatComponent implements LanguageUIComponent {

    @Autowired
    private Localization loc;

    private final LanguageUIBase owner;

    public FreeChatComponent(LanguageUIBase owner) {
        this.owner = owner;
    }

    @Override
    public String getName() {
        return loc.getValue(L.LABEL_FREE_CHAT);
    }

    @Override
    public Component create() {
        Div content = new Div();
        content.addClassName(CssStyles.FULL_SIZE);
        return content;
    }

    @Override
    public void onEnter() {
    }

    @Override
    public void onLeave() {
    }

}
