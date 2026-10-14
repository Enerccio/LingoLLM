package com.github.enerccio.lingollm.domain.traits;

import java.lang.annotation.*;

/**
 * Marks a method of an interface as a function the LLM can call. The interface is bound with
 * {@code InferenceServices.getFunctionRegistry()}: the method's parameters become the JSON schema sent to the model
 * and the arguments the model answers with are parsed into them before the method is called.
 * <p>
 * Parameters are named by {@link LLMParam#name()}, otherwise by their Java name (the project compiles with
 * {@code -parameters}). Types can be anything Jackson reads: primitives, strings, enums, collections, records and
 * beans (their fields can be described with {@code @JsonPropertyDescription}).
 * <p>
 * Functions are asynchronous. The method returns {@code void} and takes a
 * {@link com.github.enerccio.lingollm.domain.service.function.FunctionContinuation} as its <b>first</b> parameter
 * (not part of the schema); it is called on a worker thread and answers by calling {@code complete(result)} or
 * {@code fail(message)} on the continuation, from any thread, whenever it is done. The result is sent back to the
 * model: a string as it is, anything else as JSON, {@code null} as "OK". The compiler of the interface rejects methods
 * that return a value or lack the continuation.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LLMFunction {

    /** Name the model calls the function by; the method name when empty. Letters, digits, '_' and '-' only. */
    String name() default "";

    /** What the function does and when to use it; the model decides on this text. */
    String description() default "";

}
