package com.github.enerccio.lingollm.domain.service.impl;

import com.github.enerccio.lingollm.Configuration;
import com.github.enerccio.lingollm.DatabaseCheck;
import com.github.enerccio.lingollm.domain.components.CronSchedule;
import com.github.enerccio.lingollm.domain.model.impl.settings.AppSettings;
import com.github.enerccio.lingollm.domain.security.AdminGuard;
import com.github.enerccio.lingollm.domain.service.DatabaseBackupService;
import com.github.enerccio.lingollm.domain.service.SettingService;
import com.github.enerccio.lingollm.domain.traits.NoTx;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Database backups ({@code VACUUM INTO} copies in the backup folder) and their schedule.
 * <p>
 * The schedule is a cron job on a single-thread scheduler: it is started once the application context is ready and
 * restarted whenever an administrator changes the schedule. A scheduled time that passed while the application was
 * not running (typical for the desktop version) is caught up shortly after start.
 */
public class DatabaseBackupServiceImpl implements DatabaseBackupService, ApplicationListener<ContextRefreshedEvent>, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DatabaseBackupServiceImpl.class);

    private static final String MANUAL_PREFIX = "lingollm-";
    private static final String SCHEDULED_PREFIX = "lingollm-scheduled-";
    private static final String BACKUP_EXTENSION = ".sqlite";

    @Autowired
    private Configuration configuration;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SettingService settingService;

    @Autowired
    private AdminGuard adminGuard;

    /**
     * Guards the schedule state and read-modify-write of the schedule part of {@link AppSettings}.
     */
    private final Object scheduleLock = new Object();
    private final AtomicBoolean started = new AtomicBoolean();
    private ThreadPoolTaskScheduler scheduler;
    private ScheduledFuture<?> scheduledJob;
    private CronSchedule activeSchedule;
    private Duration catchUpDelay = Duration.ofSeconds(30);

    @Override
    @NoTx
    public synchronized DatabaseBackup createBackup() throws Exception {
        adminGuard.requireAdmin();
        return backup(MANUAL_PREFIX);
    }

    /**
     * VACUUM INTO produces consistent copy of live database, it can't run inside transaction.
     */
    private DatabaseBackup backup(String prefix) {
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File target = uniqueFile(prefix + timestamp);
        jdbcTemplate.execute("VACUUM INTO '" + target.getAbsolutePath().replace("'", "''") + "'");
        log.info("Database backup created: {}", target.getAbsolutePath());
        return toBackup(target);
    }

    @Override
    @NoTx
    public List<DatabaseBackup> getBackups() throws Exception {
        adminGuard.requireAdmin();
        return listBackups();
    }

    private List<DatabaseBackup> listBackups() {
        File[] files = configuration.getDatabaseBackupFolder().listFiles(f -> f.isFile() && f.getName().endsWith(BACKUP_EXTENSION));
        List<DatabaseBackup> backups = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                backups.add(toBackup(file));
            }
        }
        backups.sort(Comparator.comparing(DatabaseBackup::getCreation).reversed());
        return backups;
    }

    @Override
    @NoTx
    public File getBackupFile(DatabaseBackup backup) throws Exception {
        adminGuard.requireAdmin();
        return backupFile(backup);
    }

    private File backupFile(DatabaseBackup backup) throws IOException {
        File file = new File(configuration.getDatabaseBackupFolder(), FilenameUtils.getName(backup.getName()));
        if (!file.isFile()) {
            throw new IOException("Backup " + backup.getName() + " does not exist");
        }
        return file;
    }

    @Override
    @NoTx
    public synchronized DatabaseBackup importBackup(String fileName, File source) throws Exception {
        adminGuard.requireAdmin();
        DatabaseCheck.check(source);
        String baseName = FilenameUtils.getBaseName(StringUtils.defaultIfBlank(fileName, "uploaded"));
        File target = uniqueFile(baseName.replaceAll("[^A-Za-z0-9._-]", "_"));
        Files.copy(source.toPath(), target.toPath());
        return toBackup(target);
    }

    @Override
    @NoTx
    public synchronized void deleteBackup(DatabaseBackup backup) throws Exception {
        adminGuard.requireAdmin();
        removeBackup(backup);
    }

    private void removeBackup(DatabaseBackup backup) throws Exception {
        File file = backupFile(backup);
        Files.deleteIfExists(file.toPath());
        Files.deleteIfExists(new File(file.getAbsolutePath() + "-wal").toPath());
        Files.deleteIfExists(new File(file.getAbsolutePath() + "-shm").toPath());
    }

    @Override
    @NoTx
    public synchronized void scheduleRestore(DatabaseBackup backup) throws Exception {
        adminGuard.requireAdmin();
        File file = backupFile(backup);
        // checked again on start, but a file that can't be restored is better reported now
        DatabaseCheck.check(file);
        File pending = getPendingRestoreFile();
        File tmp = new File(pending.getAbsolutePath() + ".tmp");
        Files.copy(file.toPath(), tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Files.move(tmp.toPath(), pending.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        log.warn("Database restore from {} scheduled for next start", file.getAbsolutePath());
    }

    @Override
    @NoTx
    public synchronized void cancelRestore() throws Exception {
        adminGuard.requireAdmin();
        Files.deleteIfExists(getPendingRestoreFile().toPath());
    }

    @Override
    @NoTx
    public boolean isRestorePending() throws Exception {
        adminGuard.requireAdmin();
        return getPendingRestoreFile().exists();
    }

    @Override
    @NoTx
    public long getDatabaseSize() throws Exception {
        adminGuard.requireAdmin();
        File db = configuration.getDatabaseFile();
        long size = db.length();
        File wal = new File(db.getAbsolutePath() + "-wal");
        if (wal.exists()) {
            size += wal.length();
        }
        return size;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // schedule
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    @NoTx
    public BackupSchedule getSchedule() throws Exception {
        adminGuard.requireAdmin();
        AppSettings settings = settingService.getOrCreateApp(AppSettings.class);
        return new BackupSchedule(Boolean.TRUE.equals(settings.getBackupScheduleEnabled()),
                StringUtils.defaultString(settings.getBackupSchedule()),
                settings.getBackupKeep() == null ? 0 : settings.getBackupKeep(),
                settings.getLastScheduledBackup());
    }

    @Override
    @NoTx
    public BackupSchedule updateSchedule(boolean enabled, String cron, int keep) throws Exception {
        adminGuard.requireAdmin();
        if (keep < 0) {
            throw new IllegalArgumentException("Number of kept backups can't be negative");
        }
        // a schedule that is off may have no expression, a set one is always validated so it can be turned on later
        String expression = StringUtils.trimToEmpty(cron);
        if (enabled || !expression.isEmpty()) {
            expression = CronSchedule.parse(expression).getExpression();
        }
        synchronized (scheduleLock) {
            AppSettings settings = settingService.getOrCreateApp(AppSettings.class);
            settings.setBackupScheduleEnabled(enabled);
            settings.setBackupSchedule(expression);
            settings.setBackupKeep(keep);
            settings.setBackupScheduleChanged(new Date());
            settingService.save(settings);
            log.info("Backup schedule changed: enabled={}, cron='{}', keep={}", enabled, expression, keep);
            restartSchedule(false);
        }
        return getSchedule();
    }

    @Override
    public Date getNextScheduledBackup() {
        synchronized (scheduleLock) {
            if (activeSchedule == null) {
                return null;
            }
            ZonedDateTime next = activeSchedule.next(ZonedDateTime.now(activeSchedule.getZone()));
            return next == null ? null : Date.from(next.toInstant());
        }
    }

    @Override
    @NoTx
    public DatabaseBackup createScheduledBackup() throws Exception {
        DatabaseBackup backup;
        synchronized (this) {
            backup = backup(SCHEDULED_PREFIX);
            deleteOldScheduledBackups();
        }
        synchronized (scheduleLock) {
            AppSettings settings = settingService.getOrCreateApp(AppSettings.class);
            settings.setLastScheduledBackup(backup.getCreation());
            settingService.save(settings);
        }
        return backup;
    }

    private void deleteOldScheduledBackups() throws Exception {
        Integer keep = settingService.getOrCreateApp(AppSettings.class).getBackupKeep();
        if (keep == null || keep <= 0) {
            return;
        }
        List<DatabaseBackup> scheduled = listBackups().stream().filter(DatabaseBackup::isScheduled).toList();
        for (DatabaseBackup old : scheduled.subList(Math.min(keep, scheduled.size()), scheduled.size())) {
            log.info("Deleting old scheduled database backup {}", old.getName());
            removeBackup(old);
        }
    }

    /**
     * Starts the schedule once the application (and so the database) is ready.
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        try {
            restartSchedule(true);
        } catch (Exception e) {
            log.error("Failed to start database backup schedule", e);
        }
    }

    /**
     * Cancels the scheduled job and starts it again from the stored settings.
     *
     * @param catchUp run one backup soon when a scheduled time passed while the application was not running
     */
    public void restartSchedule(boolean catchUp) throws Exception {
        synchronized (scheduleLock) {
            if (scheduledJob != null) {
                scheduledJob.cancel(false);
                scheduledJob = null;
            }
            activeSchedule = null;

            AppSettings settings = settingService.getOrCreateApp(AppSettings.class);
            if (!Boolean.TRUE.equals(settings.getBackupScheduleEnabled())) {
                log.debug("Scheduled database backups are off");
                return;
            }
            CronSchedule schedule;
            try {
                schedule = CronSchedule.parse(settings.getBackupSchedule());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid database backup schedule '{}', scheduled backups are off: {}", settings.getBackupSchedule(), e.getMessage());
                return;
            }

            ThreadPoolTaskScheduler taskScheduler = scheduler();
            scheduledJob = taskScheduler.schedule(this::runScheduledBackup, new CronTrigger(schedule.getSpringExpression(), schedule.getZone()));
            activeSchedule = schedule;
            log.info("Scheduled database backups '{}', next at {}", schedule.getExpression(), getNextScheduledBackup());

            if (catchUp && wasMissed(schedule, settings)) {
                log.info("A scheduled database backup was missed, creating one in {}", catchUpDelay);
                taskScheduler.schedule(this::runScheduledBackup, Instant.now().plus(catchUpDelay));
            }
        }
    }

    private boolean wasMissed(CronSchedule schedule, AppSettings settings) {
        Date baseline = settings.getLastScheduledBackup();
        Date changed = settings.getBackupScheduleChanged();
        if (baseline == null || (changed != null && changed.after(baseline))) {
            baseline = changed;
        }
        if (baseline == null) {
            return false;
        }
        return schedule.wasDueBetween(ZonedDateTime.ofInstant(baseline.toInstant(), schedule.getZone()),
                ZonedDateTime.now(schedule.getZone()));
    }

    private void runScheduledBackup() {
        try {
            createScheduledBackup();
        } catch (Throwable e) {
            log.error("Scheduled database backup failed", e);
        }
    }

    private ThreadPoolTaskScheduler scheduler() {
        if (scheduler == null) {
            scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.setThreadNamePrefix("database-backup-");
            scheduler.setDaemon(true);
            scheduler.setRemoveOnCancelPolicy(true);
            scheduler.initialize();
        }
        return scheduler;
    }

    @Override
    public void destroy() {
        synchronized (scheduleLock) {
            if (scheduler != null) {
                scheduler.shutdown();
                scheduler = null;
            }
            scheduledJob = null;
            activeSchedule = null;
        }
    }

    /**
     * Delay of the catch-up backup after start (default 30 seconds, lets the application settle first).
     */
    public void setCatchUpDelay(Duration catchUpDelay) {
        this.catchUpDelay = catchUpDelay;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // files
    // ---------------------------------------------------------------------------------------------------------------

    private File getPendingRestoreFile() {
        return new File(configuration.getDatabaseFile().getAbsolutePath() + Configuration.PENDING_RESTORE_SUFFIX);
    }

    private File uniqueFile(String baseName) {
        File folder = configuration.getDatabaseBackupFolder();
        File file = new File(folder, baseName + BACKUP_EXTENSION);
        int counter = 1;
        while (file.exists()) {
            file = new File(folder, baseName + "-" + counter++ + BACKUP_EXTENSION);
        }
        return file;
    }

    private DatabaseBackup toBackup(File file) {
        DatabaseBackup backup = new DatabaseBackup();
        backup.setName(file.getName());
        backup.setCreation(new Date(file.lastModified()));
        backup.setSize(file.length());
        backup.setScheduled(file.getName().startsWith(SCHEDULED_PREFIX));
        return backup;
    }
}
