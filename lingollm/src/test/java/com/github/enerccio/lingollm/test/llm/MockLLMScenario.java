package com.github.enerccio.lingollm.test.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Programmed behaviour of the mock LLM for one test case, addressed by {@link #baseUrl()}.
 * <p>
 * Chat completion requests are answered in this order:
 * <ol>
 *     <li>queued responses ({@link #enqueue}), one per request</li>
 *     <li>the responder ({@link #respondWith}), if set</li>
 *     <li>the fallback response ({@link #fallback}), if set</li>
 *     <li>otherwise HTTP 500 "no response programmed"</li>
 * </ol>
 * Every request (including model listing) is recorded and can be inspected with {@link #getRequests()}.
 * Note that the OpenAI client retries 408/409/429/5xx responses, so an error response is consumed once per attempt.
 */
public class MockLLMScenario {

    public static final String DEFAULT_MODEL = "mock-model";

    private final MockLLMServer server;
    private final String id;
    private final Deque<MockLLMResponse> queue = new ConcurrentLinkedDeque<>();
    private final List<MockLLMRequest> requests = new CopyOnWriteArrayList<>();
    private volatile List<String> models = List.of(DEFAULT_MODEL);
    private volatile Function<MockLLMRequest, MockLLMResponse> responder;
    private volatile MockLLMResponse fallback;

    MockLLMScenario(MockLLMServer server, String id) {
        this.server = server;
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /**
     * OpenAI base url for this scenario: {@code http://127.0.0.1:<port>/<id>/v1}.
     */
    public String baseUrl() {
        return server.getRootUrl() + "/" + id + "/v1";
    }

    public MockLLMScenario models(String... models) {
        this.models = List.of(models);
        return this;
    }

    public List<String> getModels() {
        return models;
    }

    public MockLLMScenario enqueue(MockLLMResponse... responses) {
        queue.addAll(List.of(responses));
        return this;
    }

    /**
     * Shortcut for {@code enqueue(MockLLMResponse.text(text))}.
     */
    public MockLLMScenario reply(String text) {
        return enqueue(MockLLMResponse.text(text));
    }

    public MockLLMScenario respondWith(Function<MockLLMRequest, MockLLMResponse> responder) {
        this.responder = responder;
        return this;
    }

    public MockLLMScenario fallback(MockLLMResponse fallback) {
        this.fallback = fallback;
        return this;
    }

    public int getPendingResponses() {
        return queue.size();
    }

    /**
     * All requests received so far, in order.
     */
    public List<MockLLMRequest> getRequests() {
        return new ArrayList<>(requests);
    }

    public List<MockLLMRequest> getCompletionRequests() {
        return requests.stream().filter(r -> "/chat/completions".equals(r.getEndpoint())).toList();
    }

    public MockLLMRequest lastCompletionRequest() {
        List<MockLLMRequest> completions = getCompletionRequests();
        if (completions.isEmpty()) {
            throw new AssertionError("Mock LLM scenario " + id + " received no chat completion request");
        }
        return completions.getLast();
    }

    /**
     * Waits until at least {@code count} chat completion requests were received.
     */
    public List<MockLLMRequest> awaitCompletionRequests(int count, Duration timeout) throws InterruptedException, TimeoutException {
        long deadline = System.nanoTime() + timeout.toNanos();
        synchronized (requests) {
            while (getCompletionRequests().size() < count) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new TimeoutException("Expected " + count + " completion requests in scenario " + id
                            + ", got " + getCompletionRequests().size());
                }
                requests.wait(Math.max(1, remaining / 1_000_000));
            }
        }
        return getCompletionRequests();
    }

    /**
     * Clears programmed responses and recorded requests.
     */
    public MockLLMScenario reset() {
        queue.clear();
        requests.clear();
        responder = null;
        fallback = null;
        models = List.of(DEFAULT_MODEL);
        return this;
    }

    /**
     * Unregisters the scenario from the server; its base url answers 404 afterwards.
     */
    public void close() {
        server.removeScenario(this);
    }

    void record(MockLLMRequest request) {
        synchronized (requests) {
            requests.add(request);
            requests.notifyAll();
        }
    }

    MockLLMResponse nextResponse(MockLLMRequest request) {
        MockLLMResponse response = queue.poll();
        if (response != null) {
            return response;
        }
        Function<MockLLMRequest, MockLLMResponse> responder = this.responder;
        if (responder != null) {
            response = responder.apply(request);
            if (response != null) {
                return response;
            }
        }
        return fallback;
    }
}
