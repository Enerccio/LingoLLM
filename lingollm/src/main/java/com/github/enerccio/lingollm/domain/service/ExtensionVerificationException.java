package com.github.enerccio.lingollm.domain.service;

/**
 * An extension was not loaded because it doesn't fit this version of the application. The message is the verification
 * report.
 */
public class ExtensionVerificationException extends Exception {

    private final String extension;

    public ExtensionVerificationException(String extension, String report) {
        super(report);
        this.extension = extension;
    }

    /**
     * Name of the extension JAR.
     */
    public String getExtension() {
        return extension;
    }

    public String getReport() {
        return getMessage();
    }
}
