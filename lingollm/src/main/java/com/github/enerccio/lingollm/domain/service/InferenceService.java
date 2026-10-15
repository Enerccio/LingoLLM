package com.github.enerccio.lingollm.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.enerccio.lingollm.domain.service.function.FunctionContinuation;
import com.github.enerccio.lingollm.domain.service.function.FunctionSet;
import com.github.enerccio.lingollm.domain.service.dto.LLMChatMessage;
import com.github.enerccio.lingollm.domain.service.dto.LLMToolCall;
import com.github.enerccio.lingollm.domain.service.structured.SchemaCheck;
import com.github.enerccio.lingollm.domain.service.structured.StructuredOutputException;
import com.github.enerccio.lingollm.domain.service.structured.StructuredOutputs;

import java.util.List;

public interface InferenceService {

    List<String> getModels();

    /**
     * Checks that the provider works with the configured model: lists the models and makes a minimal completion.
     * Failures are thrown ({@link InferenceException} for what the provider answered).
     *
     * @return the models the provider lists, empty when it doesn't list them
     */
    default List<String> testConnection() {
        return getModels();
    }

    default CancellationToken stream(List<LLMChatMessage> payload, InferenceAsyncCallback callback) throws Exception {
        return stream(payload, (FunctionSet) null, callback);
    }

    /**
     * Streams a response for the payload and lets the model call the functions. When it does, the service calls the
     * Java methods (asynchronously, see {@link FunctionContinuation}), appends the calls and their results to its copy of the payload (the list passed in is not
     * modified), asks the model again and goes on until it answers without a call. The callback sees one continuous
     * stream of chunks and one completion; {@link InferenceAsyncCallback#onFunctionCall} reports each call.
     * <p>
     * After {@link #MAX_FUNCTION_ROUNDS} rounds of calls the model is told not to call anything, so that the stream
     * always ends with an answer.
     *
     * @param functions the functions to offer, null or empty for none; build with
     *                  {@link FunctionRegistry#bind}
     * @throws UnsupportedOperationException when the service can not call functions
     */
    CancellationToken stream(List<LLMChatMessage> payload, FunctionSet functions, InferenceAsyncCallback callback) throws Exception;

    default CancellationToken infer(List<LLMChatMessage> payload, InferenceResultCallback callback) throws Exception {
        return infer(payload, (JsonNode) null, callback);
    }

    /**
     * Asks for the whole answer at once instead of a stream. Still asynchronous: the call returns at once and the
     * callback gets the text when the model is done. Sampling parameters, limits and usage counting are those of
     * {@link #stream(List, InferenceAsyncCallback)}. The reasoning, if the provider sends it, goes to
     * {@link InferenceOutcomeCallback#onReasoning} before the result.
     *
     * @param schema JSON schema the answer must match, null for any text. The provider is asked to follow it
     *               (response format), and the answer is checked against it either way: the callback learns from
     *               {@link SchemaCheck} whether it is valid, an invalid answer is still delivered
     * @throws UnsupportedOperationException when the service can not do it
     */
    default CancellationToken infer(List<LLMChatMessage> payload, JsonNode schema,
                                    InferenceResultCallback callback) throws Exception {
        return infer(payload, schema, (FunctionSet) null, callback);
    }

    /**
     * Like {@link #infer(List, JsonNode, InferenceResultCallback)}, and the model may call the functions
     * on the way, exactly as in {@link #stream(List, FunctionSet, InferenceAsyncCallback)}: the calls run,
     * the model is asked again with the results, up to {@link #MAX_FUNCTION_ROUNDS} rounds. The callback gets one
     * result, that of the last round; {@link InferenceOutcomeCallback#onFunctionCall} reports each call.
     *
     * @param functions the functions to offer, null or empty for none; build with {@link FunctionRegistry#bind}
     * @throws UnsupportedOperationException when the service can not call functions (or can not infer)
     */
    default CancellationToken infer(List<LLMChatMessage> payload, JsonNode schema,
                                    FunctionSet functions, InferenceResultCallback callback) throws Exception {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not support infer");
    }

    /**
     * Asks for an answer in the shape of the class: its JSON schema is built from its fields ({@link
     * com.github.enerccio.lingollm.domain.traits.LLMSchema}, {@link com.github.enerccio.lingollm.domain.traits.LLMField}),
     * the answer is checked against it and read into an instance. An answer that doesn't match ends in
     * {@link TypedInferenceCallback#onError} with a {@link StructuredOutputException}.
     */
    default <T> CancellationToken infer(List<LLMChatMessage> payload, Class<T> type,
                                        TypedInferenceCallback<T> callback) throws Exception {
        return infer(payload, type, (FunctionSet) null, callback);
    }

    /** Like the above, the model may call the functions on the way (see the variant with a schema). */
    default <T> CancellationToken infer(List<LLMChatMessage> payload, Class<T> type,
                                        FunctionSet functions, TypedInferenceCallback<T> callback) throws Exception {
        return infer(payload, StructuredOutputs.schemaOf(type), functions, new InferenceResultCallback() {
            @Override
            public void onResult(String text, SchemaCheck check) throws Exception {
                if (!check.valid()) {
                    callback.onError(new StructuredOutputException(text, check.errors(), null));
                    return;
                }
                T result;
                try {
                    result = StructuredOutputs.parse(text, type);
                } catch (StructuredOutputException e) {
                    callback.onError(e);
                    return;
                }
                callback.onResult(result, text);
            }

            @Override
            public void onFunctionCall(LLMToolCall call, String result, boolean failed) throws Exception {
                callback.onFunctionCall(call, result, failed);
            }

            @Override
            public void onReasoning(String reasoning) throws Exception {
                callback.onReasoning(reasoning);
            }

            @Override
            public void onCancel() throws Exception {
                callback.onCancel();
            }

            @Override
            public void onError(Throwable exception) throws Exception {
                callback.onError(exception);
            }

            @Override
            public boolean isDead() {
                return callback.isDead();
            }
        });
    }

    /**
     * Streams a response and asks the provider to answer in the JSON schema (response format). The schema is only
     * passed on: the chunks are delivered as they are and nothing is checked, that is up to the caller (see
     * {@link StructuredOutputs#validate}; {@code infer} does it for you).
     *
     * @param schema    JSON schema for the provider, null for none
     * @param functions as in {@link #stream(List, FunctionSet, InferenceAsyncCallback)}
     * @throws UnsupportedOperationException when the service can not pass a schema
     */
    default CancellationToken stream(List<LLMChatMessage> payload, JsonNode schema, FunctionSet functions,
                                     InferenceAsyncCallback callback) throws Exception {
        if (schema != null) {
            throw new UnsupportedOperationException(getClass().getSimpleName() + " does not support a response schema");
        }
        return stream(payload, functions, callback);
    }

    /** How many times the model may call functions in one request before it has to answer. */
    int MAX_FUNCTION_ROUNDS = 10;


    enum ChunkType {
        REASONING, RESPONSE
    }

    interface InferenceAsyncCallback {

        void onChunk(InferenceAsyncController controller, ChunkType chunkType, String text) throws Exception;
        void onCompletion() throws Exception;
        void onCancel() throws Exception;
        void onError(Throwable exception) throws Exception;
        boolean isDead();

        /**
         * The model called a function and the Java method has run (or the call failed). Called on the thread the
         * function answered on, before the model is asked again; the chunks that follow are its answer to the result.
         *
         * @param result the text the model gets, for a failed call the error message
         * @param failed whether the call ended in an error instead of a result
         */
        default void onFunctionCall(LLMToolCall call, String result, boolean failed) throws Exception {
        }

    }

    /** What every asynchronous call ends in besides the result. */
    interface InferenceOutcomeCallback {

        void onCancel() throws Exception;
        void onError(Throwable exception) throws Exception;

        /** Whether nobody waits for the answer any more (the request is then dropped). */
        default boolean isDead() {
            return false;
        }

        /** Same as {@link InferenceAsyncCallback#onFunctionCall}. */
        default void onFunctionCall(LLMToolCall call, String result, boolean failed) throws Exception {
        }

        /**
         * What the model reasoned before it answered, when the provider sends it. Called just before the result, and
         * only when there is some; for a request with function calls it is the reasoning of the last round.
         */
        default void onReasoning(String reasoning) throws Exception {
        }

    }

    /** The result of {@code infer}: the whole text. */
    interface InferenceResultCallback extends InferenceOutcomeCallback {

        /**
         * @param text  what the model wrote
         * @param check whether it matches the schema; {@link SchemaCheck#checked()} is false when none was passed
         */
        void onResult(String text, SchemaCheck check) throws Exception;

    }

    /** The result of {@code infer} with a class: the answer read into an instance. */
    interface TypedInferenceCallback<T> extends InferenceOutcomeCallback {

        /**
         * @param result the answer as an object
         * @param text   what the model wrote
         */
        void onResult(T result, String text) throws Exception;

    }

    interface InferenceAsyncController {

        void continueInference();
        void terminateInference();

    }

}
