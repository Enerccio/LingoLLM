package com.github.enerccio.lingollm.test.llm;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.models.Model;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockLLMServerTest {

    private static MockLLMServer server;

    @BeforeAll
    static void start() throws Exception {
        server = MockLLMServer.start();
    }

    @AfterAll
    static void stop() {
        server.close();
    }

    private static OpenAIClient client(MockLLMScenario scenario) {
        return OpenAIOkHttpClient.builder()
                .baseUrl(scenario.baseUrl())
                .apiKey("secret")
                .maxRetries(0)
                .build();
    }

    private static ChatCompletionCreateParams params(String userMessage) {
        return ChatCompletionCreateParams.builder()
                .model("mock-model")
                .addSystemMessage("You are a test.")
                .addUserMessage(userMessage)
                .build();
    }

    @Test
    void listsScenarioModels() {
        MockLLMScenario scenario = server.scenario().models("a", "b");

        List<String> models = client(scenario).models().list().data().stream().map(Model::id).toList();

        assertThat(models).containsExactly("a", "b");
        assertThat(scenario.getRequests()).singleElement()
                .satisfies(r -> assertThat(r.getEndpoint()).isEqualTo("/models"));
    }

    @Test
    void streamsProgrammedChunksAndRecordsRequest() {
        MockLLMScenario scenario = server.scenario();
        scenario.enqueue(MockLLMResponse.chunks("Hel", "lo", " world").withReasoning("hmm"));

        String content;
        try (StreamResponse<ChatCompletionChunk> stream = client(scenario).chat().completions().createStreaming(params("Hi!"))) {
            content = stream.stream()
                    .flatMap(c -> c.choices().stream())
                    .map(c -> c.delta().content().orElse(""))
                    .collect(Collectors.joining());
        }

        assertThat(content).isEqualTo("Hello world");
        MockLLMRequest request = scenario.lastCompletionRequest();
        assertThat(request.isStream()).isTrue();
        assertThat(request.getApiKey()).isEqualTo("secret");
        assertThat(request.getModel()).isEqualTo("mock-model");
        assertThat(request.getMessages()).containsExactly(
                new MockLLMRequest.Message("system", "You are a test."),
                new MockLLMRequest.Message("user", "Hi!"));
    }

    @Test
    void answersNonStreamingRequests() {
        MockLLMScenario scenario = server.scenario().reply("first").reply("second");

        ChatCompletion first = client(scenario).chat().completions().create(params("1"));
        ChatCompletion second = client(scenario).chat().completions().create(params("2"));

        assertThat(first.choices().getFirst().message().content()).contains("first");
        assertThat(second.choices().getFirst().message().content()).contains("second");
        assertThat(scenario.getPendingResponses()).isZero();
    }

    @Test
    void scenariosAreIsolated() {
        MockLLMScenario a = server.scenario().reply("from a");
        MockLLMScenario b = server.scenario().reply("from b");

        assertThat(client(b).chat().completions().create(params("x")).choices().getFirst().message().content())
                .contains("from b");
        assertThat(client(a).chat().completions().create(params("x")).choices().getFirst().message().content())
                .contains("from a");
        assertThat(a.getRequests()).hasSize(1);
        assertThat(b.getRequests()).hasSize(1);
    }

    @Test
    void scenarioCanBeAddressedByName() {
        MockLLMScenario scenario = server.scenario("named-case").reply("named");

        assertThat(scenario.baseUrl()).endsWith("/named-case/v1");
        assertThat(client(scenario).chat().completions().create(params("x")).choices().getFirst().message().content())
                .contains("named");
        scenario.close();
    }

    @Test
    void responderAndFallback() {
        MockLLMScenario scenario = server.scenario()
                .respondWith(r -> r.getLastMessage().content().equals("echo") ? MockLLMResponse.text("echoed") : null)
                .fallback(MockLLMResponse.text("fallback"));

        assertThat(client(scenario).chat().completions().create(params("echo")).choices().getFirst().message().content())
                .contains("echoed");
        assertThat(client(scenario).chat().completions().create(params("other")).choices().getFirst().message().content())
                .contains("fallback");
    }

    @Test
    void returnsProgrammedErrors() {
        MockLLMScenario scenario = server.scenario().enqueue(MockLLMResponse.error(401, "bad key"));

        assertThatThrownBy(() -> client(scenario).chat().completions().create(params("x")))
                .isInstanceOf(OpenAIServiceException.class)
                .satisfies(e -> assertThat(((OpenAIServiceException) e).statusCode()).isEqualTo(401))
                .hasMessageContaining("bad key");
    }

    @Test
    void failsWhenNothingIsProgrammed() {
        MockLLMScenario scenario = server.scenario();

        assertThatThrownBy(() -> client(scenario).chat().completions().create(params("x")))
                .isInstanceOf(OpenAIServiceException.class)
                .hasMessageContaining("no response programmed");
    }

    @Test
    void unknownScenarioIsNotFound() {
        MockLLMScenario scenario = server.scenario();
        scenario.close();

        assertThatThrownBy(() -> client(scenario).models().list())
                .isInstanceOf(OpenAIServiceException.class)
                .satisfies(e -> assertThat(((OpenAIServiceException) e).statusCode()).isEqualTo(404));
    }
}
