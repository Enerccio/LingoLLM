package com.github.enerccio.lingollm.domain.traits;

import java.lang.annotation.Target;

/**
 * Return type of a {@link PublicAPI} method.
 */
@Target({})
public @interface APIReturn {

    /**
     * Erased declared type, {@code void.class} for {@code void}.
     */
    Class<?> type();
}
