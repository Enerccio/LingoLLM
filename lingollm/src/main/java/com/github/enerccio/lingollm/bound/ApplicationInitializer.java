package com.github.enerccio.lingollm.bound;

import com.github.enerccio.lingollm.bound.Migration.MigrationType;
import com.github.enerccio.lingollm.domain.model.impl.settings.AppSettings;
import com.github.enerccio.lingollm.domain.service.SettingService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

public class ApplicationInitializer implements InitializingBean {

    @Autowired
    private SettingService settingService;

    private int appVersion;
    private int dbVersion;
    private List<Migration> migrations = new ArrayList<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        initializeAndMigrateAppDB();
    }

    private void initializeAndMigrateAppDB() throws Exception {
        AppSettings settings = settingService.getOrCreateApp(AppSettings.class);

        int dbVer = settings.getDbVersion();
        if (dbVer < dbVersion) {
            for (Migration migration : migrations) {
                dbVer = migration.migrate(dbVer, MigrationType.DB);
            }
            settings.setDbVersion(dbVer);
        }

        int appVer = settings.getAppVersion();
        if (appVer < appVersion) {
            for (Migration migration : migrations) {
                appVer = migration.migrate(appVer, MigrationType.APP);
            }
            settings.setAppVersion(appVer);
        }

        settingService.save(settings);
    }

    public int getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(int appVersion) {
        this.appVersion = appVersion;
    }

    public int getDbVersion() {
        return dbVersion;
    }

    public void setDbVersion(int dbVersion) {
        this.dbVersion = dbVersion;
    }

    public List<Migration> getMigrations() {
        return migrations;
    }

    public void setMigrations(List<Migration> migrations) {
        this.migrations = migrations;
    }
}
