package com.github.enerccio.lingollm.crud;

import com.github.enerccio.lingollm.domain.model.Setting;
import com.github.enerccio.lingollm.domain.model.impl.settings.AppSettings;
import com.github.enerccio.lingollm.domain.model.impl.settings.UserSetting;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.service.OwnedService;
import com.github.enerccio.lingollm.domain.service.SettingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class SettingCrudTest extends ExtendableCrudContract<Setting> {

    @Autowired
    private SettingService settingService;

    @Override
    protected OwnedService<Setting, ?> service() {
        return settingService;
    }

    @Override
    protected Setting newEntity() {
        UserSetting setting = new UserSetting();
        // a key getOrCreate never looks up, so the contract tests don't clash with it
        setting.setKey(uniqueName("crud"));
        setting.setDefaultModel(11L);
        setting.setLastLanguage(12L);
        setting.setLanguage("cs");
        return setting;
    }

    @Override
    protected void assertCreated(Setting loaded) {
        assertThat(loaded).isInstanceOf(UserSetting.class);
        UserSetting setting = (UserSetting) loaded;
        assertThat(setting.getKey()).startsWith("crud-");
        assertThat(setting.getDefaultModel()).isEqualTo(11L);
        assertThat(setting.getLastLanguage()).isEqualTo(12L);
        assertThat(setting.getLanguage()).isEqualTo("cs");
    }

    @Override
    protected void modify(Setting entity) {
        UserSetting setting = (UserSetting) entity;
        setting.setDefaultModel(null);
        setting.setLanguage("de");
    }

    @Override
    protected void assertModified(Setting loaded) {
        UserSetting setting = (UserSetting) loaded;
        assertThat(setting.getDefaultModel()).isNull();
        assertThat(setting.getLanguage()).isEqualTo("de");
        assertThat(setting.getLastLanguage()).isEqualTo(12L);
    }

    @Test
    void getOrCreateReturnsSameSettingPerUser() throws Exception {
        UserSetting first = settingService.getOrCreate(UserSetting.class);
        first.setLanguage("mine");
        settingService.save(first);

        UserSetting again = settingService.getOrCreate(UserSetting.class);
        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(again.getLanguage()).isEqualTo("mine");
        assertThat(again.getOwner().getId()).isEqualTo(owner.getId());
        assertThat(again.getKey()).isEqualTo("UserSetting");

        User other = login();
        UserSetting others = settingService.getOrCreate(UserSetting.class);
        assertThat(others.getId()).isNotEqualTo(first.getId());
        assertThat(others.getOwner().getId()).isEqualTo(other.getId());
        assertThat(others.getLanguage()).isNull();

        assertThat(settingService.getOrCreate(UserSetting.class, owner).getId()).isEqualTo(first.getId());
    }

    @Test
    void appSettingsAreGlobal() throws Exception {
        AppSettings settings = settingService.getOrCreateApp(AppSettings.class);

        assertThat(settings.getOwner()).isNull();
        assertThat(settings.getDbVersion()).isNotNull();

        login();
        assertThat(settingService.getOrCreateApp(AppSettings.class).getId()).isEqualTo(settings.getId());
    }

    @Test
    void appSettingsFieldsArePersisted() throws Exception {
        AppSettings settings = settingService.getOrCreateApp(AppSettings.class);
        Integer appVersion = settings.getAppVersion();
        try {
            settings.setAppVersion(appVersion + 41);
            settingService.save(settings);

            AppSettings loaded = settingService.getOrCreateApp(AppSettings.class);
            assertThat(loaded.getAppVersion()).isEqualTo(appVersion + 41);
            assertThat(loaded).isInstanceOf(AppSettings.class);
        } finally {
            AppSettings restore = settingService.getOrCreateApp(AppSettings.class);
            restore.setAppVersion(appVersion);
            settingService.save(restore);
        }
    }

    @Test
    void settingTypeIsStoredInDiscriminator() throws Exception {
        UserSetting saved = settingService.getOrCreate(UserSetting.class);

        Setting loaded = settingService.find(saved.getId());

        assertThat(loaded).isExactlyInstanceOf(UserSetting.class);
    }
}
