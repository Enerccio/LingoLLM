package com.github.enerccio.lingollm.domain.service.function;

/**
 * The way an {@link com.github.enerccio.lingollm.domain.traits.LLMFunction} method answers. Every such method takes
 * one as its first parameter (the model does not see it) and returns {@code void}: the method is called on a thread of
 * its own, may hand the work to anything else and finishes the call, from any thread and at any time, by calling
 * exactly one of {@link #complete} or {@link #fail}. The model is asked again only after that. The first finish wins,
 * later ones are ignored.
 * <pre>
 * &#64;LLMFunction(description = "Finds lorebook entries by name")
 * void find(FunctionContinuation&lt;List&lt;String&gt;&gt; done, String name) {
 *     executor.submit(() -&gt; done.complete(lorebooks.findByName(name)));
 * }
 * </pre>
 *
 * @param <T> what the function answers: a string is sent to the model as it is, anything else as JSON
 */
public interface FunctionContinuation<T> {

    /** Finishes the call with a result; {@code null} (the answer of a function that has nothing to say) is "OK". */
    void complete(T result);

    /** Finishes the call with an error. The message goes to the model, which can correct itself. */
    void fail(String message);

    /** Finishes the call with an error; the message of the throwable goes to the model. */
    void fail(Throwable error);

    /** Whether the request is over (cancelled or its caller is gone), so that the answer is not needed any more. */
    boolean isCancelled();

}
