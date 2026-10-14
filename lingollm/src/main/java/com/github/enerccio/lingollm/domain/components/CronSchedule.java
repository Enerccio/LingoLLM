package com.github.enerccio.lingollm.domain.components;

import org.springframework.scheduling.support.CronExpression;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cron expression as typed by an administrator.
 * <p>
 * Accepts the common 5 field form ({@code minute hour day-of-month month day-of-week}, e.g. {@code 0 3 * * *}),
 * Spring's 6 field form with seconds and the macros {@code @yearly}, {@code @monthly}, {@code @weekly},
 * {@code @daily}, {@code @midnight} and {@code @hourly}.
 */
public final class CronSchedule {

    private final String expression;
    private final CronExpression cron;
    private final ZoneId zone;

    private CronSchedule(String expression, CronExpression cron, ZoneId zone) {
        this.expression = expression;
        this.cron = cron;
        this.zone = zone;
    }

    /**
     * @throws IllegalArgumentException when the expression is not valid, with a message suitable for the user
     */
    public static CronSchedule parse(String expression, ZoneId zone) {
        String normalized = normalize(expression);
        try {
            return new CronSchedule(expression.trim(), CronExpression.parse(normalized), zone);
        } catch (IllegalArgumentException e) {
            // Spring reports the normalized (6 field) expression, show the one the user typed
            String message = String.valueOf(e.getMessage()).replace("\"" + normalized + "\"", "\"" + expression.trim() + "\"");
            throw new IllegalArgumentException(message, e);
        }
    }

    public static CronSchedule parse(String expression) {
        return parse(expression, ZoneId.systemDefault());
    }

    /**
     * Spring expression for the given input: 5 fields get "0" seconds prepended.
     */
    static String normalize(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Schedule is empty");
        }
        String trimmed = expression.trim();
        if (trimmed.startsWith("@")) {
            return trimmed;
        }
        String[] fields = trimmed.split("\\s+");
        return switch (fields.length) {
            case 5 -> "0 " + String.join(" ", fields);
            case 6 -> String.join(" ", fields);
            default -> throw new IllegalArgumentException(
                    "Expected 5 fields (minute hour day-of-month month day-of-week), got " + fields.length);
        };
    }

    public String getExpression() {
        return expression;
    }

    /**
     * Spring's form of the expression, for {@link org.springframework.scheduling.support.CronTrigger}.
     */
    public String getSpringExpression() {
        return cron.toString();
    }

    public ZoneId getZone() {
        return zone;
    }

    /**
     * @return first execution strictly after {@code time}, {@code null} if there is none
     */
    public ZonedDateTime next(ZonedDateTime time) {
        return cron.next(time.withZoneSameInstant(zone));
    }

    public List<ZonedDateTime> next(ZonedDateTime from, int count) {
        List<ZonedDateTime> result = new ArrayList<>();
        ZonedDateTime time = from;
        for (int i = 0; i < count; i++) {
            time = next(time);
            if (time == null) {
                break;
            }
            result.add(time);
        }
        return result;
    }

    /**
     * True when an execution was due after {@code since} and at or before {@code now} - a run missed while the
     * application was not running.
     */
    public boolean wasDueBetween(ZonedDateTime since, ZonedDateTime now) {
        ZonedDateTime next = next(since);
        return next != null && !next.isAfter(now);
    }

    @Override
    public String toString() {
        return expression;
    }
}
