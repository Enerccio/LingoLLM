package com.github.enerccio.lingollm.domain.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Fills prompt templates: Handlebars, so {@code {{name}}} is replaced with the value of the variable and
 * {@code {{#each list}}...{{/each}}} / {@code {{#if x}}...{{/if}}} work. The result is a prompt for a model, not HTML:
 * nothing is escaped.
 */
public interface TemplateService {

    ValidationResult isValidTemplate(String template, String templateName) throws Exception;

    /**
     * Validates template like {@link #isValidTemplate(String, String)} and also reports names the template uses that
     * are not in {@code knownNames}. They are most likely typos, so they are reported in
     * {@link ValidationResult#unknownNames()} and don't make the template invalid. Paths are checked by their first
     * segment; names relative to the current context ({@code this}, {@code .}, {@code ../x}, {@code @index}) are skipped,
     * but names of the items of a list inside {@code {{#each}}} are not, so pass them too.
     *
     * @param knownNames the variables the template is rendered with, null skips the unknown names check
     */
    ValidationResult isValidTemplate(String template, String templateName, Collection<String> knownNames) throws Exception;

    /**
     * @param template the template, null gives an empty text
     * @param values   the variables of the template (values can be texts, numbers, lists, maps or beans)
     * @throws IllegalArgumentException when the template is not valid
     */
    String processTemplate(String template, String templateName, Map<String, ?> values) throws Exception;

    /**
     * @param unknownNames names that are not among the known ones (warnings)
     */
    record ValidationResult(boolean isValid, String errorMessage, List<String> unknownNames) {

        public ValidationResult(boolean isValid, String errorMessage) {
            this(isValid, errorMessage, List.of());
        }

        public ValidationResult {
            unknownNames = unknownNames == null ? List.of() : List.copyOf(unknownNames);
        }

        public boolean hasWarnings() {
            return !unknownNames.isEmpty();
        }
    }

}
