package com.github.enerccio.lingollm.test;

import com.github.enerccio.lingollm.domain.service.InferenceService.ChunkType;
import com.github.enerccio.lingollm.domain.service.InferenceService.InferenceAsyncCallback;
import com.github.enerccio.lingollm.domain.service.InferenceService.InferenceAsyncController;
import com.github.enerccio.lingollm.domain.service.dto.LLMToolCall;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * {@link InferenceAsyncCallback} that pulls the whole stream and records what it got.
 * <pre>
 * InferenceCollector collector = new InferenceCollector();
 * inferenceService.stream(payload, collector);
 * InferenceCollector.Outcome outcome = collector.await(Duration.ofSeconds(10));
 * </pre>
 */
public class InferenceCollector implements InferenceAsyncCallback {

    public enum Outcome { COMPLETED, CANCELLED, ERROR }

    public record Chunk(ChunkType type, String text) {}

    public record FunctionCall(LLMToolCall call, String result, boolean failed) {}

    private final List<Chunk> chunks = new CopyOnWriteArrayList<>();
    private final List<FunctionCall> functionCalls = new CopyOnWriteArrayList<>();
    private final CompletableFuture<Outcome> result = new CompletableFuture<>();
    private volatile Throwable error;
    private volatile boolean dead;

    @Override
    public void onChunk(InferenceAsyncController controller, ChunkType chunkType, String text) {
        chunks.add(new Chunk(chunkType, text));
        controller.continueInference();
    }

    @Override
    public void onFunctionCall(LLMToolCall call, String result, boolean failed) {
        functionCalls.add(new FunctionCall(call, result, failed));
    }

    /** The functions the model called, with what they returned, in order. */
    public List<FunctionCall> getFunctionCalls() {
        return List.copyOf(functionCalls);
    }

    @Override
    public void onCompletion() {
        result.complete(Outcome.COMPLETED);
    }

    @Override
    public void onCancel() {
        result.complete(Outcome.CANCELLED);
    }

    @Override
    public void onError(Throwable exception) {
        error = exception;
        result.complete(Outcome.ERROR);
    }

    @Override
    public boolean isDead() {
        return dead;
    }

    /**
     * Simulates a closed UI: the stream is cancelled before the next chunk.
     */
    public void kill() {
        dead = true;
    }

    public Outcome await(Duration timeout) throws Exception {
        return result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public List<Chunk> getChunks() {
        return List.copyOf(chunks);
    }

    public String getText(ChunkType type) {
        StringBuilder sb = new StringBuilder();
        for (Chunk chunk : chunks) {
            if (chunk.type() == type) {
                sb.append(chunk.text());
            }
        }
        return sb.toString();
    }

    public String getResponse() {
        return getText(ChunkType.RESPONSE);
    }

    public String getReasoning() {
        return getText(ChunkType.REASONING);
    }

    public Throwable getError() {
        return error;
    }
}
