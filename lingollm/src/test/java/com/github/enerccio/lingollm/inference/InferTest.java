package com.github.enerccio.lingollm.inference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.enerccio.lingollm.domain.service.InferenceService;
import com.github.enerccio.lingollm.domain.service.function.FunctionContinuation;
import com.github.enerccio.lingollm.domain.service.function.FunctionSet;
import com.github.enerccio.lingollm.domain.traits.LLMFunction;
import com.github.enerccio.lingollm.domain.service.InferenceService.InferenceResultCallback;
import com.github.enerccio.lingollm.domain.service.InferenceService.TypedInferenceCallback;
import com.github.enerccio.lingollm.domain.service.dto.LLMChatMessage;
import com.github.enerccio.lingollm.domain.service.dto.LLMRole;
import com.github.enerccio.lingollm.domain.service.structured.SchemaCheck;
import com.github.enerccio.lingollm.domain.service.structured.StructuredOutputException;
import com.github.enerccio.lingollm.domain.service.structured.StructuredOutputs;
import com.github.enerccio.lingollm.domain.traits.LLMField;
import com.github.enerccio.lingollm.domain.traits.LLMSchema;
import com.github.enerccio.lingollm.test.LingoLLMTestBase;
import com.github.enerccio.lingollm.test.llm.MockLLMResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code InferenceService.infer}: the whole answer at once, checked against a schema or read into a class.
 */
class InferTest extends LingoLLMTestBase {

    enum Mood { CALM, TENSE }

    @LLMSchema(description = "A character of the story")
    record Character(
            @LLMField(description = "Full name", minLength = 2) String name,
            @LLMField(minimum = 0, maximum = 150) int age,
            Mood mood,
            @LLMField(required = false, maxItems = 2) List<String> traits) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<LLMChatMessage> PAYLOAD = List.of(LLMChatMessage.of(LLMRole.USER, "Make a character."));

    private record Result(String text, SchemaCheck check, Throwable error) {}

    private static class Collect implements InferenceResultCallback {
        final CompletableFuture<Result> done = new CompletableFuture<>();

        public void onResult(String text, SchemaCheck check) {
            done.complete(new Result(text, check, null));
        }

        public void onCancel() {
            done.complete(new Result(null, null, null));
        }

        public void onError(Throwable error) {
            done.complete(new Result(null, null, error));
        }

        Result await() throws Exception {
            return done.get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void schemaOfClassUsesAnnotations() {
        JsonNode schema = StructuredOutputs.schemaOf(Character.class);

        assertThat(schema.get("description").asText()).isEqualTo("A character of the story");
        assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.get("required")).extracting(JsonNode::asText).containsExactlyInAnyOrder("name", "age", "mood");
        assertThat(schema.at("/properties/name/minLength").asInt()).isEqualTo(2);
        assertThat(schema.at("/properties/name/description").asText()).isEqualTo("Full name");
        assertThat(schema.at("/properties/age/maximum").asInt()).isEqualTo(150);
        assertThat(schema.at("/properties/traits/maxItems").asInt()).isEqualTo(2);
        assertThat(schema.at("/properties/mood/enum")).extracting(JsonNode::asText).containsExactly("CALM", "TENSE");
    }

    @Test
    void returnsWholeTextWithoutSchema() throws Exception {
        login();
        llm.enqueue(MockLLMResponse.chunks("Once ", "upon ", "a time"));

        Collect collect = new Collect();
        inferenceServices.forAI(createAI()).infer(PAYLOAD, collect);
        Result result = collect.await();

        assertThat(result.text()).isEqualTo("Once upon a time");
        assertThat(result.check().checked()).isFalse();
        assertThat(llm.lastCompletionRequest().isStream()).isFalse();
    }

    @Test
    void tellsWhetherAnswerMatchesSchema() throws Exception {
        login();
        JsonNode schema = StructuredOutputs.schemaOf(Character.class);
        InferenceService inference = inferenceServices.forAI(createAI());

        llm.enqueue(MockLLMResponse.text("```json\n{\"name\":\"Anna\",\"age\":30,\"mood\":\"CALM\"}\n```"));
        Collect valid = new Collect();
        inference.infer(PAYLOAD, schema, valid);
        assertThat(valid.await().check().valid()).isTrue();
        assertThat(llm.lastCompletionRequest().getBody().toString()).contains("response_format");

        llm.enqueue(MockLLMResponse.text("{\"name\":\"A\",\"age\":300,\"mood\":\"ANGRY\"}"));
        Collect invalid = new Collect();
        inference.infer(PAYLOAD, schema, invalid);
        SchemaCheck check = invalid.await().check();
        assertThat(check.checked()).isTrue();
        assertThat(check.valid()).isFalse();
        assertThat(check.errors()).hasSize(3);

        llm.enqueue(MockLLMResponse.text("I would rather not."));
        Collect notJson = new Collect();
        inference.infer(PAYLOAD, schema, notJson);
        assertThat(notJson.await().check().valid()).isFalse();
    }

    @Test
    void readsAnswerIntoClass() throws Exception {
        login();
        llm.enqueue(MockLLMResponse.text("{\"name\":\"Anna\",\"age\":30,\"mood\":\"TENSE\",\"traits\":[\"brave\"]}"));

        CompletableFuture<Character> done = new CompletableFuture<>();
        inferenceServices.forAI(createAI()).infer(PAYLOAD, Character.class, new TypedInferenceCallback<>() {
            public void onResult(Character result, String text) {
                done.complete(result);
            }

            public void onCancel() {
                done.cancel(false);
            }

            public void onError(Throwable error) {
                done.completeExceptionally(error);
            }
        });

        assertThat(done.get(10, TimeUnit.SECONDS)).isEqualTo(new Character("Anna", 30, Mood.TENSE, List.of("brave")));
    }

    @Test
    void typedInferFailsOnMismatch() throws Exception {
        login();
        llm.enqueue(MockLLMResponse.text("{\"name\":\"Anna\"}"));

        CompletableFuture<Throwable> done = new CompletableFuture<>();
        inferenceServices.forAI(createAI()).infer(PAYLOAD, Character.class, new TypedInferenceCallback<>() {
            public void onResult(Character result, String text) {
                done.complete(null);
            }

            public void onCancel() {
                done.complete(null);
            }

            public void onError(Throwable error) {
                done.complete(error);
            }
        });

        assertThat(done.get(10, TimeUnit.SECONDS)).isInstanceOfSatisfying(StructuredOutputException.class,
                e -> assertThat(e.getAnswer()).isEqualTo("{\"name\":\"Anna\"}"));
    }

    @Test
    void providerFailureGoesToOnError() throws Exception {
        login();
        llm.enqueue(MockLLMResponse.error(401, "bad key"));

        Collect collect = new Collect();
        inferenceServices.forAI(createAI()).infer(PAYLOAD, collect);

        assertThat(collect.await().error()).isInstanceOf(com.github.enerccio.lingollm.domain.service.InferenceException.class);
    }

    interface Cast {

        @LLMFunction(description = "Looks up a character")
        void lookup(FunctionContinuation<String> done, String name);
    }

    @Test
    void modelCanCallFunctionsBeforeAnswering() throws Exception {
        login();
        List<String> looked = new java.util.concurrent.CopyOnWriteArrayList<>();
        FunctionSet functions = inferenceServices.getFunctionRegistry().bind(Cast.class,
                (Cast) (done, name) -> {
                    looked.add(name);
                    done.complete("Anna is 30");
                });
        llm.enqueue(MockLLMResponse.toolCall("lookup", "{\"name\":\"Anna\"}"));
        llm.enqueue(MockLLMResponse.text("{\"name\":\"Anna\",\"age\":30,\"mood\":\"CALM\"}"));

        CompletableFuture<Character> done = new CompletableFuture<>();
        inferenceServices.forAI(createAI()).infer(PAYLOAD, Character.class, functions, new TypedInferenceCallback<>() {
            public void onResult(Character result, String text) {
                done.complete(result);
            }

            public void onCancel() {
                done.cancel(false);
            }

            public void onError(Throwable error) {
                done.completeExceptionally(error);
            }
        });

        assertThat(done.get(10, TimeUnit.SECONDS).age()).isEqualTo(30);
        assertThat(looked).containsExactly("Anna");
        assertThat(llm.lastCompletionRequest().getBody().toString()).contains("Anna is 30");
    }

    @Test
    void streamPassesSchemaToProviderAndChecksNothing() throws Exception {
        login();
        llm.enqueue(MockLLMResponse.chunks("not ", "json"));
        JsonNode schema = StructuredOutputs.schemaOf(Character.class);

        com.github.enerccio.lingollm.test.InferenceCollector collector = new com.github.enerccio.lingollm.test.InferenceCollector();
        inferenceServices.forAI(createAI()).stream(PAYLOAD, schema, null, collector);

        assertThat(collector.await(java.time.Duration.ofSeconds(10)))
                .isEqualTo(com.github.enerccio.lingollm.test.InferenceCollector.Outcome.COMPLETED);
        assertThat(collector.getResponse()).isEqualTo("not json");
        assertThat(llm.lastCompletionRequest().isStream()).isTrue();
        assertThat(llm.lastCompletionRequest().getBody().toString()).contains("response_format").contains("minLength");
    }
}
