package com.github.enerccio.lingollm.inference;

import com.github.enerccio.lingollm.domain.service.InferenceService;
import com.github.enerccio.lingollm.domain.service.InferenceServices;
import com.github.enerccio.lingollm.domain.service.function.FunctionContinuation;
import com.github.enerccio.lingollm.domain.service.function.FunctionSet;
import com.github.enerccio.lingollm.domain.service.dto.LLMChatMessage;
import com.github.enerccio.lingollm.domain.service.dto.LLMRole;
import com.github.enerccio.lingollm.domain.traits.LLMFunction;
import com.github.enerccio.lingollm.domain.traits.LLMParam;
import com.github.enerccio.lingollm.test.InferenceTestBase;
import com.github.enerccio.lingollm.test.InferenceCollector;
import com.github.enerccio.lingollm.test.llm.MockLLMRequest;
import com.github.enerccio.lingollm.test.llm.MockLLMResponse;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The model calls Java methods: the service runs them and asks the model again with the results.
 */
class InferenceFunctionCallTest extends InferenceTestBase {

    interface Weather {

        @LLMFunction(description = "Current weather in a city")
        void weather(FunctionContinuation<String> done, @LLMParam(description = "City name") String city);

        @LLMFunction
        void add(FunctionContinuation<Integer> done, int a, int b);

        @LLMFunction
        void explode(FunctionContinuation<String> done);

        @LLMFunction
        void refuse(FunctionContinuation<String> done);

        @LLMFunction
        void hang(FunctionContinuation<String> done);
    }

    /** Answers from threads of its own, as a function that waits for something would. */
    static class WeatherImpl implements Weather {
        final List<String> calls = new CopyOnWriteArrayList<>();
        final List<String> threads = new CopyOnWriteArrayList<>();

        private void later(Runnable answer) {
            threads.add(Thread.currentThread().getName());
            new Thread(() -> {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                answer.run();
            }, "weather-answer").start();
        }

        @Override
        public void weather(FunctionContinuation<String> done, String city) {
            calls.add("weather:" + city);
            later(() -> done.complete("Sunny in " + city));
        }

        @Override
        public void add(FunctionContinuation<Integer> done, int a, int b) {
            calls.add("add:" + a + "+" + b);
            done.complete(a + b);
        }

        @Override
        public void explode(FunctionContinuation<String> done) {
            throw new IllegalStateException("boom");
        }

        @Override
        public void hang(FunctionContinuation<String> done) {
            calls.add("hang");
        }

        @Override
        public void refuse(FunctionContinuation<String> done) {
            later(() -> done.fail("not allowed"));
        }
    }

    @Autowired
    private InferenceServices inferenceServices;

    private final WeatherImpl weather = new WeatherImpl();

    private FunctionSet functions() {
        return inferenceServices.getFunctionRegistry().bind(Weather.class, weather);
    }

    private InferenceCollector run(List<LLMChatMessage> payload) throws Exception {
        InferenceCollector collector = new InferenceCollector();
        inferenceServices.forAI(ai).stream(payload, functions(), collector);
        assertThat(collector.await(Duration.ofSeconds(10))).isEqualTo(InferenceCollector.Outcome.COMPLETED);
        return collector;
    }

    private List<LLMChatMessage> ask() {
        return List.of(LLMChatMessage.of(LLMRole.USER, "weather in Prague?"));
    }

    private static JsonObject message(MockLLMRequest request, int index) {
        return request.getBody().getAsJsonArray("messages").get(index).getAsJsonObject();
    }

    @Test
    void functionsAreSentAsToolsWithTheirSchema() throws Exception {
        llm.enqueue(MockLLMResponse.text("no need"));

        run(ask());

        JsonArray tools = llm.lastCompletionRequest().getBody().getAsJsonArray("tools");
        assertThat(tools).hasSize(5);
        JsonObject weatherTool = null;
        for (var tool : tools) {
            assertThat(tool.getAsJsonObject().get("type").getAsString()).isEqualTo("function");
            if (tool.getAsJsonObject().getAsJsonObject("function").get("name").getAsString().equals("weather")) {
                weatherTool = tool.getAsJsonObject().getAsJsonObject("function");
            }
        }
        assertThat(weatherTool).isNotNull();
        assertThat(weatherTool.get("description").getAsString()).isEqualTo("Current weather in a city");
        JsonObject city = weatherTool.getAsJsonObject("parameters").getAsJsonObject("properties").getAsJsonObject("city");
        assertThat(city.get("type").getAsString()).isEqualTo("string");
        assertThat(city.get("description").getAsString()).isEqualTo("City name");
    }

    @Test
    void requestWithoutFunctionsHasNoTools() throws Exception {
        llm.enqueue(MockLLMResponse.text("hi"));
        InferenceCollector collector = new InferenceCollector();
        inferenceServices.forAI(ai).stream(ask(), collector);
        assertThat(collector.await(Duration.ofSeconds(10))).isEqualTo(InferenceCollector.Outcome.COMPLETED);

        assertThat(llm.lastCompletionRequest().getBody().has("tools")).isFalse();
    }

    @Test
    void callIsRunAndTheModelAnswersWithTheResult() throws Exception {
        llm.enqueue(MockLLMResponse.toolCall("weather", "{\"city\":\"Prague\"}"),
                MockLLMResponse.chunks("It is ", "sunny."));

        InferenceCollector collector = run(ask());

        assertThat(weather.calls).containsExactly("weather:Prague");
        assertThat(collector.getText(InferenceService.ChunkType.RESPONSE)).isEqualTo("It is sunny.");
        assertThat(collector.getFunctionCalls()).hasSize(1);
        assertThat(collector.getFunctionCalls().getFirst().call().getName()).isEqualTo("weather");
        assertThat(collector.getFunctionCalls().getFirst().result()).isEqualTo("Sunny in Prague");
        assertThat(collector.getFunctionCalls().getFirst().failed()).isFalse();

        List<MockLLMRequest> requests = llm.getCompletionRequests();
        assertThat(requests).hasSize(2);
        MockLLMRequest second = requests.get(1);
        JsonObject assistant = message(second, 1);
        assertThat(assistant.get("role").getAsString()).isEqualTo("assistant");
        JsonObject call = assistant.getAsJsonArray("tool_calls").get(0).getAsJsonObject();
        assertThat(call.get("id").getAsString()).isEqualTo("call_mock_1");
        assertThat(call.getAsJsonObject("function").get("name").getAsString()).isEqualTo("weather");
        assertThat(call.getAsJsonObject("function").get("arguments").getAsString()).isEqualTo("{\"city\":\"Prague\"}");
        JsonObject tool = message(second, 2);
        assertThat(tool.get("role").getAsString()).isEqualTo("tool");
        assertThat(tool.get("tool_call_id").getAsString()).isEqualTo("call_mock_1");
        assertThat(tool.get("content").getAsString()).isEqualTo("Sunny in Prague");
    }

    @Test
    void functionsRunOffTheCallingThreadAndMayAnswerFromAnother() throws Exception {
        llm.enqueue(MockLLMResponse.toolCall("weather", "{\"city\":\"Prague\"}"), MockLLMResponse.text("ok"));

        run(ask());

        assertThat(weather.threads).hasSize(1).doesNotContain(Thread.currentThread().getName());
    }

    @Test
    void functionThatFailsItsContinuationIsReportedToTheModel() throws Exception {
        llm.enqueue(MockLLMResponse.toolCall("refuse", "{}"), MockLLMResponse.text("ok"));

        InferenceCollector collector = run(ask());

        assertThat(collector.getFunctionCalls().getFirst().failed()).isTrue();
        assertThat(message(llm.getCompletionRequests().get(1), 2).get("content").getAsString())
                .startsWith("Error:").contains("not allowed");
    }

    @Test
    void textBeforeTheCallIsKeptInTheConversation() throws Exception {
        llm.enqueue(MockLLMResponse.text("Let me check. ").andToolCall("add", "{\"a\":2,\"b\":40}"),
                MockLLMResponse.text("42"));

        InferenceCollector collector = run(ask());

        assertThat(collector.getText(InferenceService.ChunkType.RESPONSE)).isEqualTo("Let me check. 42");
        assertThat(message(llm.getCompletionRequests().get(1), 1).get("content").getAsString()).isEqualTo("Let me check. ");
        assertThat(message(llm.getCompletionRequests().get(1), 2).get("content").getAsString()).isEqualTo("42");
    }

    @Test
    void severalCallsInOneAnswerRunInOrder() throws Exception {
        llm.enqueue(MockLLMResponse.toolCall("weather", "{\"city\":\"Brno\"}").andToolCall("add", "{\"a\":1,\"b\":2}"),
                MockLLMResponse.text("done"));

        run(ask());

        assertThat(weather.calls).containsExactly("weather:Brno", "add:1+2");
        MockLLMRequest second = llm.getCompletionRequests().get(1);
        assertThat(message(second, 1).getAsJsonArray("tool_calls")).hasSize(2);
        assertThat(message(second, 2).get("tool_call_id").getAsString()).isEqualTo("call_mock_1");
        assertThat(message(second, 3).get("tool_call_id").getAsString()).isEqualTo("call_mock_2");
        assertThat(message(second, 3).get("content").getAsString()).isEqualTo("3");
    }

    @Test
    void modelCanCallFunctionsInSeveralRounds() throws Exception {
        llm.enqueue(MockLLMResponse.toolCall("add", "{\"a\":1,\"b\":1}"),
                MockLLMResponse.toolCall("add", "{\"a\":2,\"b\":2}"),
                MockLLMResponse.text("4"));

        InferenceCollector collector = run(ask());

        assertThat(weather.calls).containsExactly("add:1+1", "add:2+2");
        assertThat(collector.getFunctionCalls()).hasSize(2);
        assertThat(llm.getCompletionRequests()).hasSize(3);
        assertThat(llm.getCompletionRequests().get(2).getBody().getAsJsonArray("messages")).hasSize(5);
    }

    @Test
    void callerPayloadIsNotModified() throws Exception {
        llm.enqueue(MockLLMResponse.toolCall("add", "{\"a\":1,\"b\":1}"), MockLLMResponse.text("2"));
        List<LLMChatMessage> payload = new ArrayList<>(ask());

        run(payload);

        assertThat(payload).hasSize(1);
    }

    @Test
    void failedCallIsReportedToTheModelNotAsAnError() throws Exception {
        llm.enqueue(MockLLMResponse.toolCall("explode", "{}").andToolCall("add", "{\"a\":1}").andToolCall("nope", "{}"),
                MockLLMResponse.text("sorry"));

        InferenceCollector collector = run(ask());

        assertThat(collector.getText(InferenceService.ChunkType.RESPONSE)).isEqualTo("sorry");
        assertThat(collector.getFunctionCalls()).extracting(InferenceCollector.FunctionCall::failed).containsExactly(true, true, true);
        MockLLMRequest second = llm.getCompletionRequests().get(1);
        assertThat(message(second, 2).get("content").getAsString()).startsWith("Error:").contains("boom");
        assertThat(message(second, 3).get("content").getAsString()).startsWith("Error:").contains("Missing required parameter 'b'");
        assertThat(message(second, 4).get("content").getAsString()).startsWith("Error:").contains("Unknown function nope");
    }

    @Test
    void toolCallsOfThePayloadAreSentAsTheyAre() throws Exception {
        llm.enqueue(MockLLMResponse.text("fine"));
        List<LLMChatMessage> payload = List.of(
                LLMChatMessage.of(LLMRole.USER, "weather?"),
                LLMChatMessage.ofToolCalls(null, List.of(
                        com.github.enerccio.lingollm.domain.service.dto.LLMToolCall.of("c1", "weather", "{\"city\":\"Oslo\"}"))),
                LLMChatMessage.ofToolResult("c1", "Snow"));

        run(payload);

        MockLLMRequest request = llm.lastCompletionRequest();
        assertThat(message(request, 1).get("role").getAsString()).isEqualTo("assistant");
        assertThat(message(request, 1).getAsJsonArray("tool_calls").get(0).getAsJsonObject().get("id").getAsString()).isEqualTo("c1");
        assertThat(message(request, 2).get("tool_call_id").getAsString()).isEqualTo("c1");
        assertThat(weather.calls).isEmpty();
    }

    @Test
    void modelThatNeverStopsCallingIsToldToAnswerInTheLastRound() throws Exception {
        llm.respondWith(request -> request.getBody().has("tool_choice")
                ? MockLLMResponse.text("enough")
                : MockLLMResponse.toolCall("add", "{\"a\":1,\"b\":1}"));

        InferenceCollector collector = run(ask());

        assertThat(collector.getText(InferenceService.ChunkType.RESPONSE)).isEqualTo("enough");
        assertThat(llm.getCompletionRequests()).hasSize(InferenceService.MAX_FUNCTION_ROUNDS + 1);
        assertThat(llm.lastCompletionRequest().getBody().get("tool_choice").getAsString()).isEqualTo("none");
        assertThat(weather.calls).hasSize(InferenceService.MAX_FUNCTION_ROUNDS);
    }

    @Test
    void deadCallbackStopsBeforeTheFunctionRuns() throws Exception {
        llm.enqueue(MockLLMResponse.toolCall("add", "{\"a\":1,\"b\":1}"), MockLLMResponse.text("2"));
        InferenceCollector collector = new InferenceCollector();
        collector.kill();

        inferenceServices.forAI(ai).stream(ask(), functions(), collector);

        assertThat(collector.await(Duration.ofSeconds(10))).isEqualTo(InferenceCollector.Outcome.CANCELLED);
        assertThat(weather.calls).isEmpty();
        assertThat(llm.getCompletionRequests()).hasSize(1);
    }

    @Test
    void cancellingWhileAFunctionIsPendingEndsTheRequest() throws Exception {
        llm.enqueue(MockLLMResponse.toolCall("hang", "{}"), MockLLMResponse.text("never asked"));
        InferenceCollector collector = new InferenceCollector();

        var token = inferenceServices.forAI(ai).stream(ask(), functions(), collector);
        long deadline = System.currentTimeMillis() + 10_000;
        while (weather.calls.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertThat(weather.calls).containsExactly("hang");
        token.cancel();

        assertThat(collector.await(Duration.ofSeconds(10))).isEqualTo(InferenceCollector.Outcome.CANCELLED);
        assertThat(llm.getCompletionRequests()).hasSize(1);
    }

    @Test
    void usageIsRecordedForEveryRequest() throws Exception {
        llm.enqueue(MockLLMResponse.toolCall("add", "{\"a\":1,\"b\":1}").usage(10, 3), MockLLMResponse.text("2").usage(20, 1));

        run(ask());

        assertThat(llm.getCompletionRequests()).hasSize(2);
    }

    @Test
    void toolRoleCanBeOverriddenByAResolver() throws Exception {
        llm.enqueue(MockLLMResponse.toolCall("add", "{\"a\":1,\"b\":1}"), MockLLMResponse.text("2"));
        InferenceServices.ToolRoleResolver resolver = forAi -> "tool_responses";
        inferenceServices.addToolRoleResolver(resolver);
        try {
            run(ask());
        } finally {
            inferenceServices.removeToolRoleResolver(resolver);
        }

        JsonObject tool = message(llm.getCompletionRequests().get(1), 2);
        assertThat(tool.get("role").getAsString()).isEqualTo("tool_responses");
        assertThat(tool.get("tool_call_id").getAsString()).isEqualTo("call_mock_1");
        assertThat(tool.get("content").getAsString()).isEqualTo("2");
    }

    @Test
    void resolverThatHasNoAnswerLeavesTheToolRole() throws Exception {
        llm.enqueue(MockLLMResponse.toolCall("add", "{\"a\":1,\"b\":1}"), MockLLMResponse.text("2"));
        InferenceServices.ToolRoleResolver resolver = forAi -> null;
        inferenceServices.addToolRoleResolver(resolver);
        try {
            run(ask());
        } finally {
            inferenceServices.removeToolRoleResolver(resolver);
        }

        assertThat(message(llm.getCompletionRequests().get(1), 2).get("role").getAsString()).isEqualTo("tool");
        assertThat(inferenceServices.resolveToolRole(ai)).isEqualTo("tool");
    }

    @Test
    void bindingNeedsAnImplementation() {
        assertThatThrownBy(() -> inferenceServices.getFunctionRegistry().bind(Weather.class, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
