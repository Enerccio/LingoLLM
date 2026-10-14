package com.github.enerccio.lingollm.domain.service.impl;

import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.slf4j.helpers.MessageFormatter;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The SLF4J logger an extension gets from {@code OsgiService.createLogger}: the usual {@code {}} placeholders and a
 * trailing throwable work. A line goes to the log file of the extension (next to its JAR) and is chained to the
 * application log (logger {@code extension.<name>}, same level, filtered by the level of the application log). TRACE is
 * off. The file is opened for each line, so nothing is held open when the extension is removed; when it grows over
 * {@link #MAX_LOG_BYTES}, it is moved to {@code <log>.old} (replacing the previous one).
 */
final class ExtensionLogger extends LegacyAbstractLogger {

    static final long MAX_LOG_BYTES = 2L * 1024 * 1024;
    static final String OLD_SUFFIX = ".old";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    /** One lock per log file, loggers of one extension share it. */
    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();

    private final File file;
    private final org.slf4j.Logger main;

    ExtensionLogger(String name, File file) {
        this.name = name;
        this.file = file;
        this.main = LoggerFactory.getLogger("extension." + name);
    }

    /**
     * The log file of an extension JAR: {@code name.jar.log} next to it.
     */
    static File logFile(File jar) {
        return new File(jar.getAbsoluteFile().getParentFile(), jar.getName() + ".log");
    }

    static File oldLogFile(File jar) {
        return new File(logFile(jar).getPath() + OLD_SUFFIX);
    }

    @Override
    public boolean isTraceEnabled() {
        return false;
    }

    @Override
    public boolean isDebugEnabled() {
        return true;
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    protected String getFullyQualifiedCallerName() {
        return null;
    }

    @Override
    protected void handleNormalizedLoggingCall(Level level, Marker marker, String messagePattern, Object[] arguments, Throwable throwable) {
        if (level == Level.TRACE) {
            return;
        }
        FormattingTuple tuple = MessageFormatter.arrayFormat(messagePattern, arguments, throwable);
        StringBuilder line = new StringBuilder()
                .append(TIME.format(LocalDateTime.now())).append(' ')
                .append(String.format("%-5s", level)).append(" [")
                .append(Thread.currentThread().getName()).append("] ")
                .append(name).append(" - ")
                .append(tuple.getMessage()).append(System.lineSeparator());
        if (tuple.getThrowable() != null) {
            StringWriter trace = new StringWriter();
            tuple.getThrowable().printStackTrace(new PrintWriter(trace));
            line.append(trace);
        }
        write(line.toString());
        if (main.isEnabledForLevel(level)) {
            main.atLevel(level).setCause(tuple.getThrowable()).log(tuple.getMessage());
        }
    }

    private void write(String text) {
        synchronized (LOCKS.computeIfAbsent(file.getPath(), k -> new Object())) {
            try {
                if (file.length() > MAX_LOG_BYTES) {
                    Files.move(file.toPath(), new File(file.getPath() + OLD_SUFFIX).toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                Files.writeString(file.toPath(), text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                // a logger must not break the extension that uses it
                java.util.logging.Logger.getLogger(ExtensionLogger.class.getName())
                        .warning("Cannot write the extension log " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * The last {@code maxBytes} of the file as text, starting at a line start; empty when there is no file. Safe
     * against concurrent writes by the loggers.
     */
    static String tail(File file, int maxBytes) throws IOException {
        synchronized (LOCKS.computeIfAbsent(file.getPath(), k -> new Object())) {
            if (!file.isFile()) {
                return "";
            }
            long length = file.length();
            long skip = Math.max(0, length - maxBytes);
            byte[] data;
            try (var in = Files.newInputStream(file.toPath())) {
                in.skipNBytes(skip);
                data = in.readAllBytes();
            }
            String text = new String(data, StandardCharsets.UTF_8);
            if (skip > 0) {
                int newline = text.indexOf('\n');
                text = newline < 0 ? text : text.substring(newline + 1);
            }
            return text;
        }
    }

    static void delete(File file) throws IOException {
        synchronized (LOCKS.computeIfAbsent(file.getPath(), k -> new Object())) {
            Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(new File(file.getPath() + OLD_SUFFIX).toPath());
        }
    }
}
