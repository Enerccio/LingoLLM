package com.github.enerccio.lingollm.domain.service.function;

/**
 * A function call of the model that could not be carried out. The message is written for the model (it is sent back
 * as the function's result so that it can correct itself), so it says what was wrong with the call, not where in the
 * code.
 */
public class FunctionCallException extends RuntimeException {

    public enum Kind {
        /** No function of that name is bound. */
        UNKNOWN_FUNCTION,
        /** The arguments are not JSON, miss a required one or do not fit the parameter type. */
        INVALID_ARGUMENTS,
        /** The Java method threw. */
        FAILED
    }

    private final Kind kind;

    public FunctionCallException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public FunctionCallException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

}
