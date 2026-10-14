package com.github.enerccio.lingollm.ui.widgets;

import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.loc.Localization;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.apache.commons.lang3.StringUtils;

/**
 * The flag of a language with its name (and the note of the profile), for lists and tab headers.
 */
public final class LanguageItem {

    private LanguageItem() {
    }

    /**
     * A language template: the flag and the localized name of the language.
     */
    public static Component create(Localization loc, Languages template) {
        HorizontalLayout layout = row();
        layout.add(flag(template), new Span(loc.getValue(loc.getLanguageName(template))));
        return layout;
    }

    /**
     * A language profile: the flag, the name the user gave it (the name of the language when it has none) and the
     * note below it.
     */
    public static Component create(Localization loc, Language profile) {
        HorizontalLayout layout = row();
        layout.add(flag(profile.getLanguage()));

        VerticalLayout text = new VerticalLayout();
        text.setPadding(false);
        text.setMargin(false);
        text.setSpacing(false);
        text.add(new Span(label(loc, profile)));
        if (StringUtils.isNotBlank(profile.getNote())) {
            Span note = new Span(profile.getNote());
            note.addClassName(CssStyles.LANGUAGE_NOTE);
            text.add(note);
        }
        layout.add(text);
        return layout;
    }

    public static String label(Localization loc, Language profile) {
        if (StringUtils.isNotBlank(profile.getNameOverride())) {
            return profile.getNameOverride();
        }
        return profile.getLanguage() != null ? loc.getValue(loc.getLanguageName(profile.getLanguage())) : "";
    }

    public static Span flag(Languages template) {
        Span flag = new Span();
        if (template != null && StringUtils.isNotBlank(template.icon)) {
            flag.getElement().setProperty("innerHTML", template.icon);
            flag.addClassName(CssStyles.LANGUAGE_FLAG);
        }
        return flag;
    }

    private static HorizontalLayout row() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setAlignItems(Alignment.CENTER);
        layout.setSpacing(true);
        return layout;
    }
}
