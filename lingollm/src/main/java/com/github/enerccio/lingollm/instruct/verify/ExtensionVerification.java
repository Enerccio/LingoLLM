package com.github.enerccio.lingollm.instruct.verify;

import java.util.List;

/**
 * Outcome of {@link com.github.enerccio.lingollm.domain.service.ExtensionService#verifyExtension}.
 *
 * @param extension name of the extension JAR
 * @param errors    the extension asks for something this version of the application doesn't have; it must not be loaded
 * @param warnings  accesses that could not be proven right or wrong (names built at run time, optional accesses
 *                  through {@code has*}, types that depend on the runtime class); they don't fail the extension
 * @param checked   one line per verified decorator registration
 * @param report    the text written to the {@code .valid} / {@code .invalid} file
 */
public record ExtensionVerification(String extension, boolean valid, List<String> errors, List<String> warnings,
                                    List<String> checked, String report) {
}
