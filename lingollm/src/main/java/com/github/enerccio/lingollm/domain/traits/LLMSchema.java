package com.github.enerccio.lingollm.domain.traits;

import java.lang.annotation.*;

/**
 * Describes a class (or record, or enum) the model answers with, see
 * {@code InferenceService.infer(payload, protocol, Class, callback)}. Optional: the JSON schema sent to the model is
 * built from the fields of the class whether or not it is annotated; the fields are described with {@link LLMField}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LLMSchema {

    /** What the object is, shown to the model as the description of the schema. */
    String description() default "";

}
