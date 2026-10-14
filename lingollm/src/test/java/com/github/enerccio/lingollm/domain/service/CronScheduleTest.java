package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.components.CronSchedule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CronScheduleTest {

    private static final ZoneId UTC = ZoneOffset.UTC;
    /**
     * Wednesday, 2026-10-07 10:15 UTC.
     */
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 7, 10, 15, 0, 0, UTC);

    private static CronSchedule cron(String expression) {
        return CronSchedule.parse(expression, UTC);
    }

    @Test
    void fiveFieldsAreMinuteHourDayMonthWeekday() {
        CronSchedule daily = cron("0 3 * * *");

        assertThat(daily.next(NOW, 2)).containsExactly(
                ZonedDateTime.of(2026, 10, 8, 3, 0, 0, 0, UTC),
                ZonedDateTime.of(2026, 10, 9, 3, 0, 0, 0, UTC));
        assertThat(daily.getExpression()).isEqualTo("0 3 * * *");
        assertThat(daily.getSpringExpression()).isEqualTo("0 0 3 * * *");
    }

    @Test
    void stepsRangesAndWeekdays() {
        assertThat(cron("0 */6 * * *").next(NOW, 3)).extracting(ZonedDateTime::getHour).containsExactly(12, 18, 0);
        assertThat(cron("30 2 * * 1").next(NOW)).isEqualTo(ZonedDateTime.of(2026, 10, 12, 2, 30, 0, 0, UTC));
        assertThat(cron("0 9-17 * * MON-FRI").next(NOW)).isEqualTo(ZonedDateTime.of(2026, 10, 7, 11, 0, 0, 0, UTC));
        assertThat(cron("0 0 1 * *").next(NOW)).isEqualTo(ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, UTC));
    }

    @Test
    void sixFieldsIncludeSeconds() {
        assertThat(cron("*/10 * * * * *").next(NOW)).isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    void macros() {
        assertThat(cron("@daily").next(NOW)).isEqualTo(ZonedDateTime.of(2026, 10, 8, 0, 0, 0, 0, UTC));
        assertThat(cron("@hourly").next(NOW)).isEqualTo(ZonedDateTime.of(2026, 10, 7, 11, 0, 0, 0, UTC));
        assertThat(cron("@weekly").next(NOW).getDayOfWeek().getValue()).isEqualTo(7);
    }

    @Test
    void whitespaceIsTolerated() {
        assertThat(cron("  0   3 *  * *  ").next(NOW)).isEqualTo(ZonedDateTime.of(2026, 10, 8, 3, 0, 0, 0, UTC));
        assertThat(cron("  0   3 *  * *  ").getExpression()).isEqualTo("0   3 *  * *");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "* * * *", "0 0 0 * * * *", "61 * * * *", "0 25 * * *", "0 3 * * MOO", "@sometimes", "abc"})
    void invalidExpressionsAreRejected(String expression) {
        assertThatThrownBy(() -> cron(expression)).isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("null");
    }

    @Test
    void nullIsRejected() {
        assertThatThrownBy(() -> cron(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void errorQuotesTheTypedExpression() {
        assertThatThrownBy(() -> cron("61 * * * *")).hasMessageContaining("\"61 * * * *\"").hasMessageNotContaining("0 61");
    }

    @Test
    void wrongFieldCountExplainsTheFormat() {
        assertThatThrownBy(() -> cron("0 3 *")).hasMessageContaining("Expected 5 fields");
    }

    @Test
    void missedRunDetection() {
        CronSchedule daily = cron("0 3 * * *");

        // last run yesterday at 3:00, now 10:15 -> today's 3:00 was missed
        assertThat(daily.wasDueBetween(NOW.minusDays(1).withHour(3).withMinute(0), NOW)).isTrue();
        // last run today at 3:00 -> nothing missed
        assertThat(daily.wasDueBetween(NOW.withHour(3).withMinute(0), NOW)).isFalse();
        // exactly due now counts as due
        assertThat(daily.wasDueBetween(NOW.minusDays(1).withHour(3).withMinute(0), NOW.withHour(3).withMinute(0))).isTrue();
    }

    @Test
    void zoneIsRespected() {
        CronSchedule prague = CronSchedule.parse("0 3 * * *", ZoneId.of("Europe/Prague"));

        // 3:00 in Prague (CEST, +2) is 1:00 UTC
        assertThat(prague.next(NOW).withZoneSameInstant(UTC).getHour()).isEqualTo(1);
    }
}
