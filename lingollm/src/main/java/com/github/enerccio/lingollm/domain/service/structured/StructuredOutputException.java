package com.github.enerccio.lingollm.domain.service.structured;

import java.util.List;

/**
 * The model answered, but not with what the schema asks for (or the answer can not be read into the class). Passed to
 * {@code onError} of a typed {@code infer}; the answer is kept for the caller to show or retry.
 */
public class StructuredOutputException extends Exception {

    private final String answer;
    private final List<String> errors;

    public StructuredOutputException(String answer, List<String> errors, Throwable cause) {
        super("The answer of the model does not match the schema: " + String.join("; ", errors), cause);
        this.answer = answer;
        this.errors = List.copyOf(errors);
    }

    /** What the model wrote. */
    public String getAnswer() {
        return answer;
    }

    public List<String> getErrors() {
        return errors;
    }

}
