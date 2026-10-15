package com.github.enerccio.lingollm.ui.workspace;

import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base of the UI of a language: what the workspace shows for the language profile the user works with, a tab sheet
 * of {@link LanguageUIComponent}s (the level, the free chat). Until the profile has a level
 * ({@link Language#getUserGrade()}) only the {@link GradeUIComponent} is usable, the other tabs are frozen. It works
 * on a phone and on a desktop, the tabs scroll sideways when they do not fit. A language with its own exercises
 * extends it and adds its tabs in {@link #createComponents()}.
 */
@Configurable
public abstract class LanguageUIBase implements LanguageUI {

    @Autowired
    protected LanguageService languageService;

    @Autowired
    protected Localization loc;

    protected final Workspace workspace;
    protected final Languages languageTemplate;
    protected Language language;

    private TabSheet tabSheet;
    private GradeUIComponent gradeComponent;
    private final Map<Tab, LanguageUIComponent> tabToComponent = new LinkedHashMap<>();
    private LanguageUIComponent active;
    // whether the user looks at the learning tab of the workspace, so the entered component is the one visible
    private boolean shown;

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

    /**
     * The tabs of the language, the grade one first. A language adds its own after the ones of the base.
     */
    protected List<LanguageUIComponent> createComponents() {
        gradeComponent = new GradeUIComponent(this);
        List<LanguageUIComponent> components = new ArrayList<>();
        components.add(gradeComponent);
        components.add(new FreeChatComponent(this));
        return components;
    }

    @Override
    public Component create() throws Exception {
        tabSheet = new TabSheet();
        tabSheet.addClassName(CssStyles.FULL_SIZE);

        tabToComponent.clear();
        for (LanguageUIComponent component : createComponents()) {
            Tab tab = tabSheet.add(component.getName(), component.create());
            tabToComponent.put(tab, component);
        }

        active = tabToComponent.get(tabSheet.getSelectedTab());
        tabSheet.addSelectedChangeListener(event -> {
            LanguageUIComponent previous = active;
            active = tabToComponent.get(event.getSelectedTab());
            if (shown) {
                if (previous != null) {
                    previous.onLeave();
                }
                if (active != null) {
                    active.onEnter();
                }
            }
        });

        updateTabs();
        return tabSheet;
    }

    /**
     * Whether the profile has no level yet, so the user is led to the grade tab and nothing else can be used.
     */
    protected boolean isGradeMissing() {
        return language == null || language.getUserGrade() == null;
    }

    /**
     * Freezes the tabs other than the grade one while the profile has no level, and unfreezes them once it has.
     * Called when the profile changes.
     */
    protected void updateTabs() {
        if (tabSheet == null) {
            return;
        }
        boolean gradeMissing = isGradeMissing();
        for (Map.Entry<Tab, LanguageUIComponent> entry : tabToComponent.entrySet()) {
            if (entry.getValue() != gradeComponent) {
                entry.getKey().setEnabled(!gradeMissing);
            }
        }
        if (gradeMissing && gradeComponent != null) {
            tabSheet.setSelectedTab(tabOf(gradeComponent));
        }
    }

    private Tab tabOf(LanguageUIComponent component) {
        return tabToComponent.entrySet().stream()
                .filter(entry -> entry.getValue() == component)
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
    }

    /**
     * The level was saved: the profile is the saved one now and the other tabs are usable.
     */
    void gradeSaved(Language saved) {
        this.language = saved;
        updateTabs();
    }

    @Override
    public void refresh() throws Exception {
        updateTabs();
        tabToComponent.values().forEach(LanguageUIComponent::refresh);
    }

    @Override
    public void onTabSwitched() throws Exception {
        shown = true;
        if (active != null) {
            active.onEnter();
        }
    }

    @Override
    public void onTabClosed() throws Exception {
        shown = false;
        if (active != null) {
            active.onLeave();
        }
    }

    /**
     * Gives the hint shown on the grade tab that the other tabs wait for the level.
     */
    protected String getGradeRequiredText() {
        return loc.getValue(L.MSG_GRADE_REQUIRED);
    }

}
