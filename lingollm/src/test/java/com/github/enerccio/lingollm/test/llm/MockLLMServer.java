package com.github.enerccio.lingollm.test.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fake OpenAI compatible server for tests.
 * <p>
 * Every test programs its own {@link MockLLMScenario}. The scenario id is the path segment before {@code /v1}, so
 * an AI configured with {@code scenario.baseUrl()} ({@code http://127.0.0.1:<port>/<scenario>/v1}) only ever sees
 * the responses programmed for that scenario, and tests running against the same server don't interfere.
 * <p>
 * Supported endpoints (relative to the base url):
 * <ul>
 *     <li>{@code GET /models} - models of the scenario</li>
 *     <li>{@code POST /chat/completions} - streaming (SSE) and non-streaming chat completions</li>
 * </ul>
 * Use {@link #shared()} for a server that lives for the whole test JVM, or {@link #start()} for a private one.
 */
public class MockLLMServer implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(MockLLMServer.class);
    private static final Gson GSON = new Gson();

    private static MockLLMServer shared;

    private final HttpServer server;
    private final ExecutorService executor;
    private final Map<String, MockLLMScenario> scenarios = new ConcurrentHashMap<>();
    private final AtomicLong completionIds = new AtomicLong();

    private MockLLMServer(HttpServer server, ExecutorService executor) {
        this.server = server;
        this.executor = executor;
    }

    public static MockLLMServer start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        // streaming responses with delays hold their thread, so each request gets its own
        ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "mock-llm");
            thread.setDaemon(true);
            return thread;
        });
        MockLLMServer mock = new MockLLMServer(server, executor);
        server.createContext("/", mock::handle);
        server.setExecutor(executor);
        server.start();
        log.debug("Mock LLM server listening on {}", mock.getRootUrl());
        return mock;
    }

    /**
     * Server shared by all tests in the JVM, started on first use and stopped on JVM exit.
     */
    public static synchronized MockLLMServer shared() {
        if (shared == null) {
            try {
                shared = start();
            } catch (IOException e) {
                throw new IllegalStateException("Cannot start mock LLM server", e);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(shared::close));
        }
        return shared;
    }

    /**
     * New scenario with a random id.
     */
    public MockLLMScenario scenario() {
        return scenario(UUID.randomUUID().toString());
    }

    /**
     * Scenario with the given id, created if it doesn't exist. The id must be a single url path segment.
     */
    public MockLLMScenario scenario(String id) {
        if (id.isEmpty() || id.contains("/")) {
            throw new IllegalArgumentException("Scenario id must be a single path segment: " + id);
        }
        return scenarios.computeIfAbsent(id, i -> new MockLLMScenario(this, i));
    }

    public void removeScenario(MockLLMScenario scenario) {
        scenarios.remove(scenario.getId(), scenario);
    }

    public String getRootUrl() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            // /<scenario>/v1/<endpoint>
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/", 4);
            if (parts.length < 4 || !"v1".equals(parts[2])) {
                sendError(exchange, 404, "invalid_request_error", "Mock LLM: expected /<scenario>/v1/..., got " + path);
                return;
            }
            MockLLMScenario scenario = scenarios.get(parts[1]);
            if (scenario == null) {
                sendError(exchange, 404, "invalid_request_error", "Mock LLM: unknown scenario " + parts[1]);
                return;
            }

            MockLLMRequest request = MockLLMRequest.read(exchange, "/" + parts[3]);
            scenario.record(request);

            switch (exchange.getRequestMethod() + " " + request.getEndpoint()) {
                case "GET /models" -> sendModels(exchange, scenario);
                case "POST /chat/completions" -> sendCompletion(exchange, scenario, request);
                default -> sendError(exchange, 404, "invalid_request_error",
                        "Mock LLM: unsupported endpoint " + exchange.getRequestMethod() + " " + request.getEndpoint());
            }
        } catch (Throwable e) {
            log.error("Mock LLM server failed to handle request", e);
            throw e;
        }
    }

    private void sendModels(HttpExchange exchange, MockLLMScenario scenario) throws IOException {
        JsonArray data = new JsonArray();
        for (String model : scenario.getModels()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", model);
            entry.addProperty("object", "model");
            entry.addProperty("created", 0);
            entry.addProperty("owned_by", "mock");
            data.add(entry);
        }
        JsonObject body = new JsonObject();
        body.addProperty("object", "list");
        body.add("data", data);
        sendJson(exchange, 200, body);
    }

    private void sendCompletion(HttpExchange exchange, MockLLMScenario scenario, MockLLMRequest request) throws IOException {
        MockLLMResponse response = scenario.nextResponse(request);
        if (response == null) {
            sendError(exchange, 500, "server_error", "Mock LLM: no response programmed for scenario "
                    + scenario.getId() + " (request #" + scenario.getRequests().size() + ")");
            return;
        }

        response.awaitGate();

        if (response.getErrorStatus() != null) {
            sendError(exchange, response.getErrorStatus(), response.getErrorType(), response.getErrorMessage());
            return;
        }

        String id = "chatcmpl-mock-" + completionIds.incrementAndGet();
        String model = request.getModel() != null ? request.getModel() : "mock-model";
        if (request.isStream()) {
            streamCompletion(exchange, request, response, id, model);
        } else {
            sendJson(exchange, 200, completionBody(response, id, model));
        }
    }

    private void streamCompletion(HttpExchange exchange, MockLLMRequest request, MockLLMResponse response, String id, String model) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, 0);
        OutputStream out = exchange.getResponseBody();

        int sent = 0;
        boolean first = true;
        for (MockLLMResponse.Chunk chunk : response.getChunks()) {
            if (response.getDisconnectAfter() != null && sent >= response.getDisconnectAfter()) {
                // abrupt end of stream without finish_reason / [DONE]
                out.flush();
                return;
            }
            if (!first) {
                sleep(response.getChunkDelayMillis());
            }

            JsonObject delta = new JsonObject();
            if (first) {
                delta.addProperty("role", "assistant");
            }
            if (chunk.reasoning()) {
                delta.addProperty(response.getReasoningField(), chunk.text());
            } else {
                delta.addProperty("content", chunk.text());
            }
            writeEvent(out, chunkBody(id, model, delta, null));
            first = false;
            sent++;
        }

        if (response.getDisconnectAfter() != null && sent >= response.getDisconnectAfter()) {
            out.flush();
            return;
        }

        int callIndex = 0;
        for (MockLLMResponse.ToolCall call : response.getToolCalls()) {
            // first the id and the name, then the arguments in pieces, like a provider streams them
            writeToolCallDelta(out, id, model, first, toolCallDelta(callIndex, call.id(), call.name(), ""));
            first = false;
            String arguments = call.arguments();
            for (int i = 0; i < arguments.length(); i += response.getArgumentChunk()) {
                sleep(response.getChunkDelayMillis());
                writeToolCallDelta(out, id, model, false, toolCallDelta(callIndex, null, null,
                        arguments.substring(i, Math.min(arguments.length(), i + response.getArgumentChunk()))));
            }
            callIndex++;
        }

        writeEvent(out, chunkBody(id, model, new JsonObject(), response.getFinishReason()));
        if (response.getPromptTokens() != null && request.includesUsage()) {
            // the usage chunk has no choices
            JsonObject usageChunk = chunkBody(id, model, new JsonObject(), null);
            usageChunk.add("choices", new JsonArray());
            JsonObject usage = new JsonObject();
            usage.addProperty("prompt_tokens", response.getPromptTokens());
            usage.addProperty("completion_tokens", response.getCompletionTokens());
            usage.addProperty("total_tokens", response.getPromptTokens() + response.getCompletionTokens());
            usageChunk.add("usage", usage);
            writeEvent(out, usageChunk);
        }
        out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private void writeToolCallDelta(OutputStream out, String id, String model, boolean first, JsonObject toolCall) throws IOException {
        JsonObject delta = new JsonObject();
        if (first) {
            delta.addProperty("role", "assistant");
        }
        JsonArray toolCalls = new JsonArray();
        toolCalls.add(toolCall);
        delta.add("tool_calls", toolCalls);
        writeEvent(out, chunkBody(id, model, delta, null));
    }

    private JsonObject toolCallDelta(int index, String callId, String name, String arguments) {
        JsonObject function = new JsonObject();
        if (name != null) {
            function.addProperty("name", name);
        }
        function.addProperty("arguments", arguments);

        JsonObject toolCall = new JsonObject();
        toolCall.addProperty("index", index);
        if (callId != null) {
            toolCall.addProperty("id", callId);
            toolCall.addProperty("type", "function");
        }
        toolCall.add("function", function);
        return toolCall;
    }

    private JsonObject chunkBody(String id, String model, JsonObject delta, String finishReason) {
        JsonObject choice = new JsonObject();
        choice.addProperty("index", 0);
        choice.add("delta", delta);
        choice.addProperty("finish_reason", finishReason);

        JsonArray choices = new JsonArray();
        choices.add(choice);

        JsonObject body = new JsonObject();
        body.addProperty("id", id);
        body.addProperty("object", "chat.completion.chunk");
        body.addProperty("created", System.currentTimeMillis() / 1000);
        body.addProperty("model", model);
        body.add("choices", choices);
        return body;
    }

    private JsonObject completionBody(MockLLMResponse response, String id, String model) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");
        message.addProperty("content", response.getContent());
        message.add("refusal", null);
        if (!response.getToolCalls().isEmpty()) {
            JsonArray toolCalls = new JsonArray();
            for (MockLLMResponse.ToolCall call : response.getToolCalls()) {
                JsonObject function = new JsonObject();
                function.addProperty("name", call.name());
                function.addProperty("arguments", call.arguments());
                JsonObject toolCall = new JsonObject();
                toolCall.addProperty("id", call.id());
                toolCall.addProperty("type", "function");
                toolCall.add("function", function);
                toolCalls.add(toolCall);
            }
            message.add("tool_calls", toolCalls);
        }
        String reasoning = response.getReasoning();
        if (!reasoning.isEmpty()) {
            message.addProperty(response.getReasoningField(), reasoning);
        }

        JsonObject choice = new JsonObject();
        choice.addProperty("index", 0);
        choice.add("message", message);
        choice.addProperty("finish_reason", response.getFinishReason());
        choice.add("logprobs", null);

        JsonArray choices = new JsonArray();
        choices.add(choice);

        JsonObject body = new JsonObject();
        body.addProperty("id", id);
        body.addProperty("object", "chat.completion");
        body.addProperty("created", System.currentTimeMillis() / 1000);
        body.addProperty("model", model);
        body.add("choices", choices);
        return body;
    }

    private void writeEvent(OutputStream out, JsonObject body) throws IOException {
        out.write(("data: " + GSON.toJson(body) + "\n\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private void sendError(HttpExchange exchange, int status, String type, String message) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("message", message);
        error.addProperty("type", type);
        error.add("param", null);
        error.add("code", null);
        JsonObject body = new JsonObject();
        body.add("error", error);
        // a client that retries must not reuse this connection (a stale pooled one ends with "unexpected end of stream")
        exchange.getResponseHeaders().set("Connection", "close");
        sendJson(exchange, status, body);
    }

    private void sendJson(HttpExchange exchange, int status, JsonObject body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
