package com.github.enerccio.lingollm.test.llm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Request received by the mock LLM, with helpers to inspect the chat completion payload.
 */
public class MockLLMRequest {

    public record Message(String role, String content) {}

    private final String method;
    private final String endpoint;
    private final Map<String, String> headers;
    private final String rawBody;
    private final JsonObject body;

    private MockLLMRequest(String method, String endpoint, Map<String, String> headers, String rawBody) {
        this.method = method;
        this.endpoint = endpoint;
        this.headers = headers;
        this.rawBody = rawBody;
        JsonObject parsed = null;
        if (!rawBody.isBlank()) {
            JsonElement element = JsonParser.parseString(rawBody);
            if (element.isJsonObject()) {
                parsed = element.getAsJsonObject();
            }
        }
        this.body = parsed != null ? parsed : new JsonObject();
    }

    static MockLLMRequest read(HttpExchange exchange, String endpoint) throws IOException {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, String.join(",", values)));
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return new MockLLMRequest(exchange.getRequestMethod(), endpoint, headers, body);
    }

    public String getMethod() {
        return method;
    }

    /**
     * Path after {@code /v1}, e.g. {@code /chat/completions}.
     */
    public String getEndpoint() {
        return endpoint;
    }

    public String getHeader(String name) {
        return headers.get(name);
    }

    /**
     * Bearer token sent by the client.
     */
    public String getApiKey() {
        String auth = getHeader("Authorization");
        return auth != null && auth.startsWith("Bearer ") ? auth.substring("Bearer ".length()) : null;
    }

    public String getRawBody() {
        return rawBody;
    }

    /**
     * Whether the request asked for the token counts in the stream ({@code stream_options.include_usage}).
     */
    public boolean includesUsage() {
        return body.has("stream_options") && body.get("stream_options").isJsonObject()
                && body.getAsJsonObject("stream_options").has("include_usage")
                && body.getAsJsonObject("stream_options").get("include_usage").getAsBoolean();
    }

    public JsonObject getBody() {
        return body;
    }

    public String getModel() {
        return body.has("model") ? body.get("model").getAsString() : null;
    }

    public boolean isStream() {
        return body.has("stream") && body.get("stream").getAsBoolean();
    }

    public List<Message> getMessages() {
        List<Message> messages = new ArrayList<>();
        if (!body.has("messages")) {
            return messages;
        }
        for (JsonElement element : body.getAsJsonArray("messages")) {
            JsonObject message = element.getAsJsonObject();
            messages.add(new Message(message.get("role").getAsString(), contentOf(message.get("content"))));
        }
        return messages;
    }

    public List<String> getMessages(String role) {
        return getMessages().stream().filter(m -> m.role().equals(role)).map(Message::content).toList();
    }

    public Message getLastMessage() {
        List<Message> messages = getMessages();
        return messages.isEmpty() ? null : messages.getLast();
    }

    /**
     * All message contents joined, handy for "prompt contains X" assertions.
     */
    public String getPromptText() {
        StringBuilder sb = new StringBuilder();
        for (Message message : getMessages()) {
            sb.append('[').append(message.role()).append("]\n").append(message.content()).append('\n');
        }
        return sb.toString();
    }

    private static String contentOf(JsonElement content) {
        if (content == null || content.isJsonNull()) {
            return null;
        }
        if (content.isJsonPrimitive()) {
            return content.getAsString();
        }
        // content parts: [{"type":"text","text":"..."}]
        StringBuilder sb = new StringBuilder();
        for (JsonElement part : content.getAsJsonArray()) {
            JsonObject partObject = part.getAsJsonObject();
            if (partObject.has("text")) {
                sb.append(partObject.get("text").getAsString());
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return method + " " + endpoint + " " + rawBody;
    }
}
