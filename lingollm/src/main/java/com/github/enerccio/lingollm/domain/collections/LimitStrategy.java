package com.github.enerccio.lingollm.domain.collections;

/**
 * What a user may do with a shared AI, see {@code AILimit}.
 */
public enum LimitStrategy {

    /** No limit, the same as having no entry. */
    UNLIMITED,
    /** Up to the configured number of tokens per {@link ResetStrategy period}. */
    TOKEN_LIMIT,
    /** The user can not use the AI at all. */
    NOT_AVAILABLE

}
