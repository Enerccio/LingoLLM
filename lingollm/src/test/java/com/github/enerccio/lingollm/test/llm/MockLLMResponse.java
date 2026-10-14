package com.github.enerccio.lingollm.test.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * One programmed answer of the mock LLM.
 * <pre>
 * MockLLMResponse.text("Hello world")                       // single content chunk
 * MockLLMResponse.chunks("Hel", "lo").chunkDelay(ofMillis(50))
 * MockLLMResponse.text("answer").withReasoning("thinking")  // reasoning chunk(s) first
 * MockLLMResponse.error(429, "slow down")
 * MockLLMResponse.chunks("a", "b", "c").disconnectAfter(2)  // stream ends abruptly
 * </pre>
 * For non-streaming requests the chunks are joined into one message.
 */
public class MockLLMResponse {

    public record Chunk(boolean reasoning, String text) {}

    /** A function call of the model; the arguments are streamed in pieces of {@code argumentChunk} characters. */
    public record ToolCall(String id, String name, String arguments) {}

    private final List<Chunk> chunks = new ArrayList<>();
    private final List<ToolCall> toolCalls = new ArrayList<>();
    private int argumentChunk = 7;
    private String finishReason = "stop";
    private String reasoningField = "reasoning_content";
    private long chunkDelayMillis;
    private Integer disconnectAfter;
    private Long promptTokens;
    private Long completionTokens;
    private CountDownLatch gate;
    private long gateTimeoutMillis = TimeUnit.SECONDS.toMillis(30);

    private Integer errorStatus;
    private String errorType;
    private String errorMessage;

    private MockLLMResponse() {
    }

    public static MockLLMResponse text(String text) {
        return chunks(text);
    }

    public static MockLLMResponse chunks(String... chunks) {
        MockLLMResponse response = new MockLLMResponse();
        for (String chunk : chunks) {
            response.chunks.add(new Chunk(false, chunk));
        }
        return response;
    }

    /**
     * Splits the text into chunks of at most {@code size} characters.
     */
    public static MockLLMResponse split(String text, int size) {
        MockLLMResponse response = new MockLLMResponse();
        for (int i = 0; i < text.length(); i += size) {
            response.chunks.add(new Chunk(false, text.substring(i, Math.min(text.length(), i + size))));
        }
        return response;
    }

    public static MockLLMResponse empty() {
        return new MockLLMResponse();
    }

    public static MockLLMResponse error(int status, String message) {
        MockLLMResponse response = new MockLLMResponse();
        response.errorStatus = status;
        response.errorType = status == 401 ? "authentication_error"
                : status == 429 ? "rate_limit_error"
                : status >= 500 ? "server_error"
                : "invalid_request_error";
        response.errorMessage = message;
        return response;
    }

    /**
     * Reasoning chunks sent before the content.
     */
    public MockLLMResponse withReasoning(String... reasoning) {
        List<Chunk> prefix = new ArrayList<>();
        for (String text : reasoning) {
            prefix.add(new Chunk(true, text));
        }
        chunks.addAll(0, prefix);
        return this;
    }

    /**
     * Answer that calls functions (after the text chunks, if any); the finish reason is {@code tool_calls}.
     * <pre>
     * MockLLMResponse.toolCall("get_weather", "{\"city\":\"Prague\"}")
     * MockLLMResponse.text("Let me look").toolCall("a", "{}").toolCall("b", "{}")
     * </pre>
     */
    public static MockLLMResponse toolCall(String name, String arguments) {
        return empty().andToolCall(name, arguments);
    }

    public MockLLMResponse andToolCall(String name, String arguments) {
        toolCalls.add(new ToolCall("call_mock_" + (toolCalls.size() + 1), name, arguments));
        finishReason = "tool_calls";
        return this;
    }

    /**
     * Size of the pieces the arguments of the calls are streamed in.
     */
    public MockLLMResponse argumentChunk(int size) {
        this.argumentChunk = size;
        return this;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public int getArgumentChunk() {
        return argumentChunk;
    }

    /**
     * Appends a content chunk.
     */
    public MockLLMResponse then(String text) {
        chunks.add(new Chunk(false, text));
        return this;
    }

    /**
     * Appends a reasoning chunk.
     */
    public MockLLMResponse thenReasoning(String text) {
        chunks.add(new Chunk(true, text));
        return this;
    }

    /**
     * Delta property used for reasoning, {@code reasoning_content} (default) or {@code reasoning}.
     */
    public MockLLMResponse reasoningField(String field) {
        this.reasoningField = field;
        return this;
    }

    /**
     * Token counts the mock reports in the last chunk of a stream, like a provider that was asked for
     * {@code stream_options.include_usage}. Without it the stream carries no counts.
     */
    public MockLLMResponse usage(long promptTokens, long completionTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        return this;
    }

    public Long getPromptTokens() {
        return promptTokens;
    }

    public Long getCompletionTokens() {
        return completionTokens;
    }

    public MockLLMResponse finishReason(String finishReason) {
        this.finishReason = finishReason;
        return this;
    }

    public MockLLMResponse chunkDelay(Duration delay) {
        this.chunkDelayMillis = delay.toMillis();
        return this;
    }

    /**
     * Ends the stream after {@code chunks} chunks without finish reason and {@code [DONE]}.
     */
    public MockLLMResponse disconnectAfter(int chunks) {
        this.disconnectAfter = chunks;
        return this;
    }

    /**
     * Server holds the response until the latch is released (or 30s pass), useful for "generation in progress" states.
     */
    public MockLLMResponse waitFor(CountDownLatch gate) {
        this.gate = gate;
        return this;
    }

    public MockLLMResponse waitFor(CountDownLatch gate, Duration timeout) {
        this.gate = gate;
        this.gateTimeoutMillis = timeout.toMillis();
        return this;
    }

    public List<Chunk> getChunks() {
        return chunks;
    }

    public String getContent() {
        return chunks.stream().filter(c -> !c.reasoning()).map(Chunk::text).collect(Collectors.joining());
    }

    public String getReasoning() {
        return chunks.stream().filter(Chunk::reasoning).map(Chunk::text).collect(Collectors.joining());
    }

    public String getFinishReason() {
        return finishReason;
    }

    public String getReasoningField() {
        return reasoningField;
    }

    public long getChunkDelayMillis() {
        return chunkDelayMillis;
    }

    public Integer getDisconnectAfter() {
        return disconnectAfter;
    }

    public Integer getErrorStatus() {
        return errorStatus;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    void awaitGate() {
        if (gate == null) {
            return;
        }
        try {
            gate.await(gateTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
