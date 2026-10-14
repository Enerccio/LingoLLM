package com.github.enerccio.lingollm.ui.workspace;

import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

/**
 * Base of the UI of a language: what the workspace shows for the language profile the user works with. A language
 * with its own exercises extends it and overrides {@link #create()}.
 */
@Configurable
public abstract class LanguageUIBase implements LanguageUI {

    @Autowired
    protected LanguageService languageService;

    protected final Workspace workspace;
    protected final Languages languageTemplate;
    protected Language language;

    public LanguageUIBase(Languages language, Workspace workspace) {
        this.languageTemplate = language;
        this.workspace = workspace;
    }

    @Override
    public String getIcon() {
        return languageTemplate.icon;
    }

    @Override
    public Language getLanguage() {
        return language;
    }

    @Override
    public void setLanguage(Language language) {
        this.language = language;
    }

    @Override
    public Component create() throws Exception {
        Span empty = new Span();
        empty.addClassName(CssStyles.FULL_SIZE);
        return empty;
    }

    @Override
    public void refresh() throws Exception {

    }

    @Override
    public void onTabSwitched() throws Exception {

    }

    @Override
    public void onTabClosed() throws Exception {

    }

}
