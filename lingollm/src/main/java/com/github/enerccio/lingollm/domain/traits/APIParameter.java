package com.github.enerccio.lingollm.domain.traits;

import java.lang.annotation.Target;

/**
 * An argument of a {@link PublicAPI} method, under the name the decorator's {@code getMethodArgument} uses.
 */
@Target({})
public @interface APIParameter {

    /**
     * Name of the parameter in the source.
     */
    String name();

    /**
     * Erased declared type (primitives as {@code int.class}, not their wrappers).
     */
    Class<?> type();
}
