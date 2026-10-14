package com.github.enerccio.lingollm.domain.traits;

import java.lang.annotation.*;

/**
 * Describes a field of a class the model answers with, see
 * {@code InferenceService.infer(payload, protocol, Class, callback)}: the constraints go into the JSON schema, the
 * answer is checked against it before it is deserialized. Put it on the field, the getter or the component of a record.
 * Optional: a field without it is required and has no constraints. The property is named as the Java field (or
 * {@code @JsonProperty}).
 * <p>
 * The limits are ignored when they keep their default; the ones that don't fit the type of the field (a
 * {@code minLength} of a number) are ignored too.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
public @interface LLMField {

    /** What the field holds, shown to the model. */
    String description() default "";

    /** Whether the model has to write it. An optional field that is missing is null (0 / false for primitives). */
    boolean required() default true;

    /** Smallest value of a number (inclusive). */
    double minimum() default Double.NEGATIVE_INFINITY;

    /** Largest value of a number (inclusive). */
    double maximum() default Double.POSITIVE_INFINITY;

    /** Shortest text. */
    int minLength() default -1;

    /** Longest text. */
    int maxLength() default -1;

    /** Fewest elements of an array or a list. */
    int minItems() default -1;

    /** Most elements of an array or a list. */
    int maxItems() default -1;

}
