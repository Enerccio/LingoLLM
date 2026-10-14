package com.github.enerccio.lingollm.backup;

import com.github.enerccio.lingollm.domain.components.CronSchedule;
import com.github.enerccio.lingollm.domain.model.impl.settings.AppSettings;
import com.github.enerccio.lingollm.domain.service.DatabaseBackupService.BackupSchedule;
import com.github.enerccio.lingollm.domain.service.DatabaseBackupService.DatabaseBackup;
import com.github.enerccio.lingollm.domain.service.SettingService;
import com.github.enerccio.lingollm.domain.service.impl.DatabaseBackupServiceImpl;
import com.github.enerccio.lingollm.test.ExpectedLog;
import com.github.enerccio.lingollm.test.LingoLLMTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Scheduled database backups: settings, the cron job (started, restarted on change, stopped), rotation of scheduled
 * backups and catching up a run missed while the application was down.
 * <p>
 * The schedule is shared by the whole Spring context, so each test turns it off again and the context is discarded
 * after the class.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DatabaseBackupScheduleTest extends LingoLLMTestBase {

    @Autowired
    private DatabaseBackupServiceImpl backupService;

    @Autowired
    private SettingService settingService;

    @BeforeEach
    void loginAsAdmin() throws Exception {
        loginAs(createUser(uniqueName("admin"), DEFAULT_PASSWORD, true));
        backupService.setCatchUpDelay(Duration.ofMillis(100));
        backupService.updateSchedule(false, "0 3 * * *", 7);
        for (DatabaseBackup backup : backupService.getBackups()) {
            backupService.deleteBackup(backup);
        }
    }

    @AfterEach
    void turnScheduleOff() throws Exception {
        loginAs(createUser(uniqueName("admin"), DEFAULT_PASSWORD, true));
        backupService.updateSchedule(false, "0 3 * * *", 7);
        backupService.setCatchUpDelay(Duration.ofSeconds(30));
    }

    private List<DatabaseBackup> scheduledBackups() throws Exception {
        return backupService.getBackups().stream().filter(DatabaseBackup::isScheduled).toList();
    }

    private static boolean waitFor(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(100);
        }
        return condition.getAsBoolean();
    }

    private int scheduledCount() {
        try {
            return scheduledBackups().size();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- settings ---------------------------------------------------------------------------------------------------

    @Test
    void scheduleIsStoredInAppSettings() throws Exception {
        BackupSchedule saved = backupService.updateSchedule(true, " 30 2 * * 1 ", 3);

        assertThat(saved).isEqualTo(new BackupSchedule(true, "30 2 * * 1", 3, null));
        AppSettings settings = settingService.getOrCreateApp(AppSettings.class);
        assertThat(settings.getBackupScheduleEnabled()).isTrue();
        assertThat(settings.getBackupSchedule()).isEqualTo("30 2 * * 1");
        assertThat(settings.getBackupKeep()).isEqualTo(3);
        assertThat(settings.getBackupScheduleChanged()).isCloseTo(new Date(), 10_000);
        assertThat(backupService.getSchedule()).isEqualTo(saved);
    }

    @Test
    void savingStartsTheJobWithTheNewSchedule() throws Exception {
        backupService.updateSchedule(true, "30 2 * * 1", 7);

        ZonedDateTime expected = CronSchedule.parse("30 2 * * 1").next(ZonedDateTime.now());
        assertThat(backupService.getNextScheduledBackup()).isEqualTo(Date.from(expected.toInstant()));

        backupService.updateSchedule(true, "0 4 * * *", 7);
        ZonedDateTime changed = CronSchedule.parse("0 4 * * *").next(ZonedDateTime.now());
        assertThat(backupService.getNextScheduledBackup()).isEqualTo(Date.from(changed.toInstant()));
    }

    @Test
    void disablingStopsTheJob() throws Exception {
        backupService.updateSchedule(true, "0 3 * * *", 7);
        assertThat(backupService.getNextScheduledBackup()).isNotNull();

        backupService.updateSchedule(false, "0 3 * * *", 7);

        assertThat(backupService.getNextScheduledBackup()).isNull();
        assertThat(backupService.getSchedule().enabled()).isFalse();
    }

    @Test
    void disabledScheduleKeepsItsExpression() throws Exception {
        BackupSchedule saved = backupService.updateSchedule(false, " 30 2 * * 1 ", 3);

        assertThat(saved.enabled()).isFalse();
        assertThat(saved.cron()).isEqualTo("30 2 * * 1");
        assertThat(backupService.getNextScheduledBackup()).isNull();

        backupService.updateSchedule(true, saved.cron(), 3);
        assertThat(backupService.getNextScheduledBackup()).isNotNull();
    }

    @Test
    void disabledScheduleMayHaveNoExpression() throws Exception {
        backupService.updateSchedule(true, "0 3 * * *", 5);

        BackupSchedule saved = backupService.updateSchedule(false, "  ", 5);

        assertThat(saved.enabled()).isFalse();
        assertThat(saved.cron()).isEmpty();
        assertThat(backupService.getNextScheduledBackup()).isNull();
    }

    @Test
    void invalidScheduleIsRejectedEvenWhenDisabled() throws Exception {
        backupService.updateSchedule(false, "0 3 * * *", 5);

        assertThatThrownBy(() -> backupService.updateSchedule(false, "garbage", 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> backupService.updateSchedule(true, "", 5)).isInstanceOf(IllegalArgumentException.class);

        assertThat(backupService.getSchedule().enabled()).isFalse();
        assertThat(backupService.getSchedule().cron()).isEqualTo("0 3 * * *");
    }

    @Test
    void invalidScheduleIsRejectedAndNothingChanges() throws Exception {
        backupService.updateSchedule(true, "0 3 * * *", 5);

        assertThatThrownBy(() -> backupService.updateSchedule(true, "0 25 * * *", 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> backupService.updateSchedule(true, "0 3 * * *", -1)).isInstanceOf(IllegalArgumentException.class);

        assertThat(backupService.getSchedule()).isEqualTo(new BackupSchedule(true, "0 3 * * *", 5, null));
        assertThat(backupService.getNextScheduledBackup()).isNotNull();
    }

    @Test
    void onlyAdministratorsChangeTheSchedule() throws Exception {
        login();

        assertThatThrownBy(() -> backupService.updateSchedule(true, "@daily", 1)).isInstanceOf(SecurityException.class);

        currentUser.setId(null);
        assertThatThrownBy(() -> backupService.updateSchedule(true, "@daily", 1)).isInstanceOf(SecurityException.class);
        loginAdmin();
        assertThat(backupService.getSchedule().enabled()).isFalse();
    }

    @Test
    void onlyAdministratorsUseDatabaseBackups() throws Exception {
        DatabaseBackup existing = backupService.createBackup();
        login();

        assertThatThrownBy(() -> backupService.createBackup()).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> backupService.getBackups()).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> backupService.getBackupFile(existing)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> backupService.deleteBackup(existing)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> backupService.scheduleRestore(existing)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> backupService.cancelRestore()).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> backupService.isRestorePending()).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> backupService.getDatabaseSize()).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> backupService.getSchedule()).isInstanceOf(SecurityException.class);

        loginAdmin();
        assertThat(backupService.getBackups()).extracting(DatabaseBackup::getName).contains(existing.getName());
    }

    // ---- running ----------------------------------------------------------------------------------------------------

    @Test
    void jobCreatesScheduledBackups() throws Exception {
        // every second
        backupService.updateSchedule(true, "* * * * * *", 0);

        assertThat(waitFor(() -> scheduledCount() >= 1, Duration.ofSeconds(10))).as("scheduled backup created").isTrue();

        BackupSchedule schedule = backupService.getSchedule();
        assertThat(schedule.lastRun()).isNotNull().isCloseTo(new Date(), 10_000);
        assertThat(scheduledBackups().getFirst().getName()).startsWith("lingollm-scheduled-");
    }

    @Test
    void stoppedJobCreatesNoMoreBackups() throws Exception {
        backupService.updateSchedule(true, "* * * * * *", 0);
        assertThat(waitFor(() -> scheduledCount() >= 1, Duration.ofSeconds(10))).isTrue();

        backupService.updateSchedule(false, "* * * * * *", 0);
        Thread.sleep(1500);
        int afterStop = scheduledCount();
        Thread.sleep(2500);

        assertThat(scheduledCount()).isEqualTo(afterStop);
    }

    @Test
    void scheduledBackupIsAValidDatabase() throws Exception {
        DatabaseBackup backup = backupService.createScheduledBackup();

        assertThat(backup.isScheduled()).isTrue();
        assertThat(backup.getSize()).isPositive();
        byte[] header = java.nio.file.Files.readAllBytes(backupService.getBackupFile(backup).toPath());
        assertThat(new String(header, 0, 15, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("SQLite format 3");
        assertThat(backupService.getSchedule().lastRun()).isEqualTo(backup.getCreation());
    }

    @Test
    void oldScheduledBackupsAreDeletedManualOnesKept() throws Exception {
        backupService.updateSchedule(false, "0 3 * * *", 2);
        DatabaseBackup manual = backupService.createBackup();
        List<String> created = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            created.add(backupService.createScheduledBackup().getName());
            Thread.sleep(20);
        }

        assertThat(scheduledBackups()).extracting(DatabaseBackup::getName).containsExactlyInAnyOrder(created.get(2), created.get(3));
        assertThat(backupService.getBackups()).extracting(DatabaseBackup::getName).contains(manual.getName());
        assertThat(backupService.getBackups().stream().filter(b -> !b.isScheduled())).hasSize(1);
    }

    @Test
    void keepZeroKeepsAll() throws Exception {
        backupService.updateSchedule(false, "0 3 * * *", 0);

        for (int i = 0; i < 3; i++) {
            backupService.createScheduledBackup();
            Thread.sleep(20);
        }

        assertThat(scheduledBackups()).hasSize(3);
    }

    // ---- start / catch up -------------------------------------------------------------------------------------------

    private void pretendLastRun(Date lastRun, Date changed) throws Exception {
        AppSettings settings = settingService.getOrCreateApp(AppSettings.class);
        settings.setLastScheduledBackup(lastRun);
        settings.setBackupScheduleChanged(changed);
        settingService.save(settings);
    }

    private static Date daysAgo(int days) {
        return new Date(System.currentTimeMillis() - days * 24L * 60 * 60 * 1000);
    }

    @Test
    void missedRunIsCaughtUpOnStart() throws Exception {
        backupService.updateSchedule(true, "0 3 * * *", 0);
        pretendLastRun(daysAgo(2), daysAgo(3));

        backupService.restartSchedule(true);

        assertThat(waitFor(() -> scheduledCount() == 1, Duration.ofSeconds(10))).as("catch-up backup").isTrue();
        assertThat(backupService.getNextScheduledBackup()).isNotNull();
    }

    @Test
    void scheduleChangedAfterTheMissedTimeIsNotCaughtUp() throws Exception {
        backupService.updateSchedule(true, "0 3 * * *", 0);
        // changed just now: no 3:00 passed since then
        pretendLastRun(daysAgo(5), new Date());

        backupService.restartSchedule(true);
        Thread.sleep(1500);

        assertThat(scheduledCount()).isZero();
    }

    @Test
    void recentRunIsNotCaughtUp() throws Exception {
        backupService.updateSchedule(true, "0 3 * * *", 0);
        pretendLastRun(new Date(), daysAgo(3));

        backupService.restartSchedule(true);
        Thread.sleep(1500);

        assertThat(scheduledCount()).isZero();
    }

    @Test
    void restartingWithoutCatchUpDoesNotRunMissedBackups() throws Exception {
        backupService.updateSchedule(true, "0 3 * * *", 0);
        pretendLastRun(daysAgo(2), daysAgo(3));

        backupService.restartSchedule(false);
        Thread.sleep(1500);

        assertThat(scheduledCount()).isZero();
    }

    @Test
    void disabledScheduleIsNotCaughtUp() throws Exception {
        pretendLastRun(daysAgo(2), daysAgo(3));

        backupService.restartSchedule(true);
        Thread.sleep(1500);

        assertThat(scheduledCount()).isZero();
        assertThat(backupService.getNextScheduledBackup()).isNull();
    }

    @Test
    void invalidStoredScheduleTurnsBackupsOff() throws Exception {
        AppSettings settings = settingService.getOrCreateApp(AppSettings.class);
        settings.setBackupScheduleEnabled(true);
        settings.setBackupSchedule("not a cron");
        settingService.save(settings);

        try (ExpectedLog log = ExpectedLog.capture(DatabaseBackupServiceImpl.class)) {
            backupService.restartSchedule(true);

            assertThat(log.warnings()).singleElement().asString().contains("not a cron");
        }

        assertThat(backupService.getNextScheduledBackup()).isNull();
    }
}
