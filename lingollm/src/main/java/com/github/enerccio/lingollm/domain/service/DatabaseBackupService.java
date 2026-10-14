package com.github.enerccio.lingollm.domain.service;

import java.io.File;
import java.util.Date;
import java.util.List;

public interface DatabaseBackupService {

    DatabaseBackup createBackup() throws Exception;

    List<DatabaseBackup> getBackups() throws Exception;

    File getBackupFile(DatabaseBackup backup) throws Exception;

    /**
     * Copies an uploaded database into the backup folder.
     *
     * @throws IllegalArgumentException when the file is not a LingoLLM database this version can restore (see
     *                                  {@link com.github.enerccio.lingollm.DatabaseCheck}), the message says why
     */
    DatabaseBackup importBackup(String fileName, File source) throws Exception;

    void deleteBackup(DatabaseBackup backup) throws Exception;

    /**
     * Stages the backup to replace the database on the next start.
     *
     * @throws IllegalArgumentException when the backup can't be restored, like {@link #importBackup(String, File)}
     */
    void scheduleRestore(DatabaseBackup backup) throws Exception;

    void cancelRestore() throws Exception;

    boolean isRestorePending() throws Exception;

    long getDatabaseSize() throws Exception;

    /**
     * Current backup schedule from {@link com.github.enerccio.lingollm.domain.model.impl.settings.AppSettings}.
     */
    BackupSchedule getSchedule() throws Exception;

    /**
     * Saves the schedule and restarts the scheduled job with it. Administrators only.
     *
     * @throws IllegalArgumentException when the cron expression is invalid
     * @throws SecurityException when the current user is not an administrator
     */
    BackupSchedule updateSchedule(boolean enabled, String cron, int keep) throws Exception;

    /**
     * @return when the scheduled job runs next, {@code null} when scheduled backups are off
     */
    Date getNextScheduledBackup();

    /**
     * Creates a scheduled backup and deletes scheduled backups over the configured count. Called by the scheduled
     * job, never deletes manual backups.
     */
    DatabaseBackup createScheduledBackup() throws Exception;

    /**
     * @param lastRun last scheduled backup, {@code null} if none was made yet
     */
    record BackupSchedule(boolean enabled, String cron, int keep, Date lastRun) {
    }

    class DatabaseBackup {
        private String name;
        private Date creation;
        private long size;
        private boolean scheduled;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Date getCreation() {
            return creation;
        }

        public void setCreation(Date creation) {
            this.creation = creation;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        /**
         * Made by the backup schedule (and rotated by it), not by an administrator.
         */
        public boolean isScheduled() {
            return scheduled;
        }

        public void setScheduled(boolean scheduled) {
            this.scheduled = scheduled;
        }
    }
}
