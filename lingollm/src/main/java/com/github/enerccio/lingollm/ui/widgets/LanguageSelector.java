package com.github.enerccio.lingollm.ui.widgets;

import com.github.enerccio.lingollm.bound.SessionManager;
import com.github.enerccio.lingollm.bound.SessionPoint;
import com.github.enerccio.lingollm.domain.model.impl.settings.UserSetting;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.service.SettingService;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Language;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.utils.UIUtils;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.util.ArrayList;

/**
 * Selector of the language of the UI, offers the languages that have a bundle. A change is saved to the settings of
 * the user, set to the session and the page is reloaded in every UI of every session of the user, the texts are
 * created when the page is built. The language is read from the settings again when the workspace is opened.
 */
@Configurable(preConstruction = true)
public class LanguageSelector extends ComboBox<Language> {

    @Autowired
    private Localization loc;

    @Autowired
    private SettingService settingService;

    @Autowired
    private SessionPoint sessionPoint;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private User currentUser;

    public LanguageSelector() {
        setLabel(loc.getValue(L.LABEL_LANGUAGE));
        setAllowCustomValue(false);
        setClearButtonVisible(false);
        setItemLabelGenerator(loc::getDisplayName);
        setItems(loc.getAvailableLanguages());
        setValue(loc.getCurrentLanguage());
        addValueChangeListener(e -> {
            if (e.isFromClient() && e.getValue() != null) {
                change(e.getValue());
            }
        });
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        // a language can be installed by an extension after the workspace was created
        setItems(loc.getAvailableLanguages());
        setValue(loc.getCurrentLanguage());
    }

    private void change(Language language) {
        try {
            UserSetting setting = settingService.getOrCreate(UserSetting.class);
            setting.setLanguage(language.code());
            settingService.save(setting);
            sessionPoint.setLanguage(language);
            reloadAll();
        } catch (Exception ex) {
            UIUtils.internalServerError(loc, ex);
        }
    }

    /**
     * Reloads every UI of the user: other sessions pick the language from the settings when their workspace opens.
     */
    private void reloadAll() {
        Long userId = currentUser.getId();
        // bound to the UI: the callback runs with the session of the other sessions locked, which getUIs() needs
        sessionManager.runForUsers(user -> userId.equals(user.getId()), sinfo -> {
            for (UI ui : new ArrayList<>(sinfo.boundVaadinSession.getUIs())) {
                if (!ui.isClosing()) {
                    ui.getPage().reload();
                }
            }
        }, true, true);

        for (UI ui : new ArrayList<>(VaadinSession.getCurrent().getUIs())) {
            if (!ui.isClosing()) {
                ui.getPage().reload();
            }
        }
    }

}
