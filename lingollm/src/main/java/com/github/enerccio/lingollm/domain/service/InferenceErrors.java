package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import org.apache.commons.lang3.StringUtils;

/**
 * Human-readable texts of {@link InferenceException}.
 */
public final class InferenceErrors {

    private InferenceErrors() {
    }

    /**
     * The inference exception in the cause chain of the throwable, null when it is not an inference failure.
     */
    public static InferenceException find(Throwable throwable) {
        for (int depth = 0; throwable != null && depth < 10; depth++) {
            if (throwable instanceof InferenceException e) {
                return e;
            }
            throwable = throwable.getCause();
        }
        return null;
    }

    /**
     * Localized text of the failure with the provider's message, null when the throwable is not an inference failure.
     */
    public static String describe(Localization loc, Throwable throwable) {
        InferenceException e = find(throwable);
        if (e == null) {
            return null;
        }
        String text = loc.getValue(key(e.getType()));
        if (StringUtils.isBlank(e.getProviderMessage())) {
            return text;
        }
        return String.format(loc.getValue(L.ERROR_INFERENCE_DETAILS), text, StringUtils.abbreviate(e.getProviderMessage(), 400));
    }

    /**
     * Text of a failed request for showing in the UI: {@link #describe} for provider failures, the message of the
     * exception otherwise.
     */
    public static String messageOf(Localization loc, Throwable throwable) {
        String description = describe(loc, throwable);
        return description != null ? description : throwable.getMessage();
    }

    private static L key(InferenceException.Type type) {
        return switch (type) {
            case AUTHENTICATION -> L.ERROR_INFERENCE_AUTHENTICATION;
            case PERMISSION -> L.ERROR_INFERENCE_PERMISSION;
            case MODEL_NOT_FOUND -> L.ERROR_INFERENCE_MODEL_NOT_FOUND;
            case NOT_FOUND -> L.ERROR_INFERENCE_NOT_FOUND;
            case CONTEXT_OVERFLOW -> L.ERROR_INFERENCE_CONTEXT_OVERFLOW;
            case RATE_LIMIT -> L.ERROR_INFERENCE_RATE_LIMIT;
            case SERVER -> L.ERROR_INFERENCE_SERVER;
            case TIMEOUT -> L.ERROR_INFERENCE_TIMEOUT;
            case CONNECTION -> L.ERROR_INFERENCE_CONNECTION;
            case BAD_REQUEST -> L.ERROR_INFERENCE_BAD_REQUEST;
            case NOT_AVAILABLE -> L.ERROR_INFERENCE_NOT_AVAILABLE;
            case LIMIT_REACHED -> L.ERROR_INFERENCE_LIMIT_REACHED;
            case UNKNOWN -> L.ERROR_INFERENCE_UNKNOWN;
        };
    }
}
