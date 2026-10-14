package com.github.enerccio.lingollm.test;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Captures what a logger writes while a test exercises a failure path on purpose, so expected errors are asserted
 * instead of printed (and mistaken for test failures).
 * <pre>
 * try (ExpectedLog log = ExpectedLog.capture(ExtensionServiceImpl.class)) {
 *     ...
 *     assertThat(log.errors()).anyMatch(m -> m.contains("broken"));
 * }
 * </pre>
 */
public final class ExpectedLog extends AppenderSkeleton implements AutoCloseable {

    public record Entry(Level level, String message, Throwable throwable) {
    }

    private final Logger logger;
    private final boolean additivity;
    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    private ExpectedLog(Logger logger) {
        this.logger = logger;
        this.additivity = logger.getAdditivity();
        logger.addAppender(this);
        // don't pass captured events to the console appender of the root logger
        logger.setAdditivity(false);
    }

    public static ExpectedLog capture(String loggerName) {
        return new ExpectedLog(Logger.getLogger(loggerName));
    }

    public static ExpectedLog capture(Class<?> loggerClass) {
        return new ExpectedLog(Logger.getLogger(loggerClass));
    }

    @Override
    protected void append(LoggingEvent event) {
        Throwable throwable = event.getThrowableInformation() == null ? null : event.getThrowableInformation().getThrowable();
        entries.add(new Entry(event.getLevel(), event.getRenderedMessage(), throwable));
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public List<String> atLeast(Level level) {
        return entries.stream().filter(e -> e.level().isGreaterOrEqual(level)).map(Entry::message).toList();
    }

    public List<String> errors() {
        return atLeast(Level.ERROR);
    }

    public List<String> warnings() {
        return atLeast(Level.WARN);
    }

    @Override
    public void close() {
        logger.removeAppender(this);
        logger.setAdditivity(additivity);
    }

    @Override
    public boolean requiresLayout() {
        return false;
    }
}
