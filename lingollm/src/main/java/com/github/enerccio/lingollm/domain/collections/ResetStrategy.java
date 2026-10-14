package com.github.enerccio.lingollm.domain.collections;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;

/**
 * How often the used tokens of a token limit start from zero. Calendar periods in the time zone of the server: a
 * day, a week (from Monday) or a month. Nothing resets by a timer, the period a usage belongs to is worked out when it
 * is read or added to, see {@link #periodStart}.
 */
public enum ResetStrategy {

    DAILY,
    WEEKLY,
    MONTHLY;

    /**
     * @return the start of the period that contains {@code now}
     */
    public Date periodStart(Date now) {
        return Date.from(start(ZonedDateTime.ofInstant(now.toInstant(), ZoneId.systemDefault())).toInstant());
    }

    /**
     * @return the start of the period after the one that contains {@code now}, when the used tokens count from zero again
     */
    public Date nextReset(Date now) {
        ZonedDateTime start = start(ZonedDateTime.ofInstant(now.toInstant(), ZoneId.systemDefault()));
        ZonedDateTime next = switch (this) {
            case DAILY -> start.plusDays(1);
            case WEEKLY -> start.plusWeeks(1);
            case MONTHLY -> start.plusMonths(1);
        };
        return Date.from(next.toInstant());
    }

    private ZonedDateTime start(ZonedDateTime now) {
        ZonedDateTime day = now.toLocalDate().atStartOfDay(now.getZone());
        return switch (this) {
            case DAILY -> day;
            case WEEKLY -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> day.with(TemporalAdjusters.firstDayOfMonth());
        };
    }
}
