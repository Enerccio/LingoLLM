package com.github.enerccio.lingollm.domain.traits;

import java.lang.annotation.*;

/**
 * Describes a parameter of an {@link LLMFunction} method. Optional: without it the parameter is required and named
 * as in the source.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface LLMParam {

    /** Name of the argument in the JSON; the Java parameter name when empty. */
    String name() default "";

    /** What the argument is, shown to the model next to its type. */
    String description() default "";

    /** Whether the model has to pass it. A missing optional argument is null (0 / false for primitives). */
    boolean required() default true;

}
