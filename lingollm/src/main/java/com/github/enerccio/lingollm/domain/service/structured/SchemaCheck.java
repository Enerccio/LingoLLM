package com.github.enerccio.lingollm.domain.service.structured;

import java.util.List;

/**
 * Whether an answer of the model matches the JSON schema it was asked for.
 *
 * @param checked whether there was a schema at all; if not, the answer is neither valid nor invalid
 * @param valid   the answer is JSON and the schema accepts it (true when not checked)
 * @param errors  what is wrong with the answer, empty when valid
 */
public record SchemaCheck(boolean checked, boolean valid, List<String> errors) {

    private static final SchemaCheck NOT_CHECKED = new SchemaCheck(false, true, List.of());
    private static final SchemaCheck VALID = new SchemaCheck(true, true, List.of());

    public SchemaCheck {
        errors = List.copyOf(errors);
    }

    public static SchemaCheck notChecked() {
        return NOT_CHECKED;
    }

    public static SchemaCheck ok() {
        return VALID;
    }

    public static SchemaCheck invalid(List<String> errors) {
        return new SchemaCheck(true, false, errors);
    }

}
