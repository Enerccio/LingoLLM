package com.github.enerccio.lingollm.domain.traits;

import java.lang.annotation.Target;

/**
 * A local variable of a {@link PublicAPI} method, under the name the decorator's {@code getLocalVariable} uses.
 */
@Target({})
public @interface APILocals {

    /**
     * Name of the variable in the source.
     */
    String name();

    /**
     * Erased declared type of the variable.
     */
    Class<?> type();
}
