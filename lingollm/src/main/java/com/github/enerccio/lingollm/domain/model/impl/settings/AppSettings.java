package com.github.enerccio.lingollm.domain.model.impl.settings;

import com.github.enerccio.lingollm.domain.model.Setting;
import com.github.enerccio.lingollm.domain.traits.ExtendedAttribute;
import jakarta.persistence.*;

import java.util.Date;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorValue("AppSettings")
public class AppSettings extends Setting {

    @Transient
    @ExtendedAttribute
    private Integer dbVersion = 1;

    @Transient
    @ExtendedAttribute
    private Integer appVersion = 1;

    // scheduled database backups

    @Transient
    @ExtendedAttribute
    private Boolean backupScheduleEnabled = false;

    /**
     * Cron expression, 5 fields (minute hour day-of-month month day-of-week), 6 fields with seconds or a macro
     * like {@code @daily}.
     */
    @Transient
    @ExtendedAttribute
    private String backupSchedule = "0 3 * * *";

    /**
     * Number of scheduled backups kept, older ones are deleted. 0 keeps all.
     */
    @Transient
    @ExtendedAttribute
    private Integer backupKeep = 7;

    @Transient
    @ExtendedAttribute
    private Date lastScheduledBackup;

    /**
     * When the schedule was last changed - scheduled runs missed before this time are not caught up.
     */
    @Transient
    @ExtendedAttribute
    private Date backupScheduleChanged;

    // AIs

    /**
     * Whether users who are not administrators can create their own AIs. When off they can only use the AIs the
     * administrators share; the AIs they already have keep working. Null (never set) means allowed.
     */
    @Transient
    @ExtendedAttribute
    private Boolean userAisAllowed = true;

    public Integer getDbVersion() {
        return dbVersion;
    }

    public void setDbVersion(Integer dbVersion) {
        this.dbVersion = dbVersion;
    }

    public Integer getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(Integer appVersion) {
        this.appVersion = appVersion;
    }

    public Boolean getBackupScheduleEnabled() {
        return backupScheduleEnabled;
    }

    public void setBackupScheduleEnabled(Boolean backupScheduleEnabled) {
        this.backupScheduleEnabled = backupScheduleEnabled;
    }

    public String getBackupSchedule() {
        return backupSchedule;
    }

    public void setBackupSchedule(String backupSchedule) {
        this.backupSchedule = backupSchedule;
    }

    public Integer getBackupKeep() {
        return backupKeep;
    }

    public void setBackupKeep(Integer backupKeep) {
        this.backupKeep = backupKeep;
    }

    public Date getLastScheduledBackup() {
        return lastScheduledBackup;
    }

    public void setLastScheduledBackup(Date lastScheduledBackup) {
        this.lastScheduledBackup = lastScheduledBackup;
    }

    public Date getBackupScheduleChanged() {
        return backupScheduleChanged;
    }

    public void setBackupScheduleChanged(Date backupScheduleChanged) {
        this.backupScheduleChanged = backupScheduleChanged;
    }

    public Boolean getUserAisAllowed() {
        return userAisAllowed == null || userAisAllowed;
    }

    public void setUserAisAllowed(Boolean userAisAllowed) {
        this.userAisAllowed = userAisAllowed;
    }
}
