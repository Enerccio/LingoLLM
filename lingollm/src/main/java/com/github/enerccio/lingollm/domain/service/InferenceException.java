package com.github.enerccio.lingollm.domain.service;

/**
 * A failed request to an inference provider, classified so the user sees what to fix instead of an internal error.
 * The provider's own message is kept for the details.
 */
public class InferenceException extends RuntimeException {

    public enum Type {
        /** Bad or missing API key (401). */
        AUTHENTICATION,
        /** The key is valid but not allowed to do this (403). */
        PERMISSION,
        /** The provider doesn't know the configured model. */
        MODEL_NOT_FOUND,
        /** Nothing at the configured URL (404), usually a wrong base URL. */
        NOT_FOUND,
        /** The prompt plus the response don't fit into the context of the model. */
        CONTEXT_OVERFLOW,
        /** Rate limit or exhausted quota (429), also after the configured retries. */
        RATE_LIMIT,
        /** The provider failed (5xx), also after the configured retries. */
        SERVER,
        /** The provider didn't answer in time. */
        TIMEOUT,
        /** The provider can't be reached (unknown host, refused connection, TLS...). */
        CONNECTION,
        /** The provider rejected the request for another reason. */
        BAD_REQUEST,
        /** An administrator made the shared AI unavailable for the user; nothing was sent to the provider. */
        NOT_AVAILABLE,
        /** The user used up the token limit an administrator set for them; nothing was sent to the provider. */
        LIMIT_REACHED,
        UNKNOWN
    }

    private final Type type;
    private final Integer statusCode;
    private final String providerMessage;

    public InferenceException(Type type, Integer statusCode, String providerMessage, Throwable cause) {
        super(type + (providerMessage != null ? ": " + providerMessage : ""), cause);
        this.type = type;
        this.statusCode = statusCode;
        this.providerMessage = providerMessage;
    }

    public Type getType() {
        return type;
    }

    /** HTTP status of the answer, null when there was none (timeout, connection). */
    public Integer getStatusCode() {
        return statusCode;
    }

    /** Message the provider sent (or the network error), may be null. */
    public String getProviderMessage() {
        return providerMessage;
    }
}
