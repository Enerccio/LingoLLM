package com.github.enerccio.lingollm.function;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.enerccio.lingollm.domain.service.FunctionRegistry;
import com.github.enerccio.lingollm.domain.service.function.FunctionCallException;
import com.github.enerccio.lingollm.domain.service.function.FunctionCallException.Kind;
import com.github.enerccio.lingollm.domain.service.function.FunctionContinuation;
import com.github.enerccio.lingollm.domain.service.function.FunctionDefinition;
import com.github.enerccio.lingollm.domain.service.function.FunctionSet;
import com.github.enerccio.lingollm.domain.service.impl.FunctionRegistryImpl;
import com.github.enerccio.lingollm.domain.traits.LLMFunction;
import com.github.enerccio.lingollm.domain.traits.LLMParam;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Java interfaces become function schemas, and the JSON the model writes is parsed into the arguments of the call.
 */
class FunctionRegistryTest {

    enum Mood { CALM, TENSE }

    record Place(@JsonPropertyDescription("Name of the place") String name, int population) {}

    interface Story {

        @LLMFunction(description = "Adds a character to the cast")
        void addCharacter(FunctionContinuation<String> done,
                          @LLMParam(description = "Full name") String name,
                          int age,
                          Mood mood,
                          @LLMParam(required = false) List<String> traits);

        @LLMFunction(name = "set_place")
        void place(FunctionContinuation<Place> done, Place place);

        @LLMFunction
        void clear(FunctionContinuation<Void> done);

        @LLMFunction
        void total(FunctionContinuation<Integer> done, List<Integer> numbers, Map<String, Double> weights);

        @LLMFunction
        void fail(FunctionContinuation<String> done, String reason);

        @LLMFunction
        void failAsync(FunctionContinuation<String> done, String reason);

        // not offered to the model
        String hidden();
    }

    static class StoryImpl implements Story {
        final List<String> calls = new ArrayList<>();
        final List<String> threads = new ArrayList<>();

        @Override
        public void addCharacter(FunctionContinuation<String> done, String name, int age, Mood mood, List<String> traits) {
            calls.add(name + "|" + age + "|" + mood + "|" + traits);
            threads.add(Thread.currentThread().getName());
            done.complete("added " + name);
        }

        @Override
        public void place(FunctionContinuation<Place> done, Place place) {
            done.complete(new Place(place.name().toUpperCase(), place.population() + 1));
        }

        @Override
        public void clear(FunctionContinuation<Void> done) {
            calls.clear();
            done.complete(null);
        }

        @Override
        public void total(FunctionContinuation<Integer> done, List<Integer> numbers, Map<String, Double> weights) {
            done.complete(numbers.stream().mapToInt(Integer::intValue).sum() + weights.size());
        }

        @Override
        public void fail(FunctionContinuation<String> done, String reason) {
            throw new IllegalStateException(reason);
        }

        @Override
        public void failAsync(FunctionContinuation<String> done, String reason) {
            new Thread(() -> done.fail(new IllegalStateException(reason))).start();
        }

        @Override
        public String hidden() {
            return "hidden";
        }
    }

    private final FunctionRegistry registry = new FunctionRegistryImpl();
    private final StoryImpl story = new StoryImpl();

    private static final Executor POOL = task -> new Thread(task, "function-test").start();

    /** Calls on another thread and waits; failures come out as the FunctionCallException the future carries. */
    private static String call(FunctionSet functions, String name, String arguments) {
        CompletableFuture<String> future = functions.call(name, arguments, POOL, () -> false);
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw (RuntimeException) e.getCause();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private FunctionDefinition definition(String name) {
        return registry.definitionsOf(Story.class).stream().filter(d -> d.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void onlyAnnotatedMethodsAreFunctions() {
        assertThat(registry.definitionsOf(Story.class)).extracting(FunctionDefinition::getName)
                .containsExactlyInAnyOrder("addCharacter", "set_place", "clear", "total", "fail", "failAsync");
    }

    @Test
    void definitionsAreCompiledOnce() {
        assertThat(registry.definitionsOf(Story.class)).isSameAs(registry.definitionsOf(Story.class));
        assertThat(registry.bind(Story.class, story).getDefinitions().getFirst())
                .isSameAs(registry.bind(Story.class, new StoryImpl()).getDefinitions().getFirst());
    }

    @Test
    void schemaDescribesTheParameters() {
        FunctionDefinition add = definition("addCharacter");
        assertThat(add.getDescription()).isEqualTo("Adds a character to the cast");

        JsonNode schema = add.getParametersSchema();
        assertThat(schema.get("type").asText()).isEqualTo("object");
        assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.has("$schema")).isFalse();

        JsonNode properties = schema.get("properties");
        assertThat(properties.get("name").get("type").asText()).isEqualTo("string");
        assertThat(properties.get("name").get("description").asText()).isEqualTo("Full name");
        assertThat(properties.get("age").get("type").asText()).isEqualTo("integer");
        assertThat(properties.get("mood").get("enum")).extracting(JsonNode::asText).containsExactly("CALM", "TENSE");
        assertThat(properties.get("traits").get("type").asText()).isEqualTo("array");
        assertThat(properties.get("traits").get("items").get("type").asText()).isEqualTo("string");
        assertThat(schema.get("required")).extracting(JsonNode::asText).containsExactly("name", "age", "mood");
    }

    @Test
    void schemaOfARecordIsInlined() {
        JsonNode place = definition("set_place").getParametersSchema().get("properties").get("place");
        assertThat(place.get("type").asText()).isEqualTo("object");
        assertThat(place.get("properties").get("name").get("description").asText()).isEqualTo("Name of the place");
        assertThat(place.get("properties").get("population").get("type").asText()).isEqualTo("integer");
    }

    @Test
    void functionWithoutParametersHasEmptyProperties() {
        JsonNode schema = definition("clear").getParametersSchema();
        assertThat(schema.get("properties")).isEmpty();
        assertThat(schema.has("required")).isFalse();
    }

    @Test
    void argumentsAreParsedIntoTheParameters() {
        FunctionSet functions = registry.bind(Story.class, story);

        String result = call(functions, "addCharacter",
                "{\"name\":\"Ada\",\"age\":36,\"mood\":\"TENSE\",\"traits\":[\"brave\",\"curious\"]}");

        assertThat(result).isEqualTo("added Ada");
        assertThat(story.calls).containsExactly("Ada|36|TENSE|[brave, curious]");
    }

    @Test
    void recordsCollectionsAndMapsAreParsed() {
        FunctionSet functions = registry.bind(Story.class, story);

        assertThat(call(functions, "set_place", "{\"place\":{\"name\":\"Oz\",\"population\":9}}"))
                .isEqualTo("{\"name\":\"OZ\",\"population\":10}");
        assertThat(call(functions, "total", "{\"numbers\":[1,2,3],\"weights\":{\"a\":0.5,\"b\":1}}")).isEqualTo("8");
    }

    @Test
    void optionalArgumentMayBeMissingAndUnknownOnesAreIgnored() {
        FunctionSet functions = registry.bind(Story.class, story);

        call(functions, "addCharacter", "{\"name\":\"Bo\",\"age\":1,\"mood\":\"CALM\",\"extra\":true}");

        assertThat(story.calls).containsExactly("Bo|1|CALM|null");
    }

    @Test
    void voidResultIsOkAndStringResultIsNotQuoted() {
        FunctionSet functions = registry.bind(Story.class, story);

        assertThat(call(functions, "clear", "")).isEqualTo("OK");
        assertThat(call(functions, "clear", null)).isEqualTo("OK");
        assertThat(call(functions, "clear", "{}")).isEqualTo("OK");
    }

    @Test
    void invalidCallsAreRefusedWithAMessageForTheModel() {
        FunctionSet functions = registry.bind(Story.class, story);

        assertThatThrownBy(() -> call(functions, "nope", "{}"))
                .isInstanceOfSatisfying(FunctionCallException.class, e -> {
                    assertThat(e.getKind()).isEqualTo(Kind.UNKNOWN_FUNCTION);
                    assertThat(e.getMessage()).contains("nope", "addCharacter");
                });
        assertThatThrownBy(() -> call(functions, "addCharacter", "{\"name\":\"x\"}"))
                .isInstanceOfSatisfying(FunctionCallException.class, e -> {
                    assertThat(e.getKind()).isEqualTo(Kind.INVALID_ARGUMENTS);
                    assertThat(e.getMessage()).contains("age");
                });
        assertThatThrownBy(() -> call(functions, "addCharacter", "{\"name\":\"x\",\"age\":\"old\",\"mood\":\"CALM\"}"))
                .isInstanceOfSatisfying(FunctionCallException.class, e -> {
                    assertThat(e.getKind()).isEqualTo(Kind.INVALID_ARGUMENTS);
                    assertThat(e.getMessage()).contains("age");
                });
        assertThatThrownBy(() -> call(functions, "addCharacter", "{\"name\":"))
                .isInstanceOfSatisfying(FunctionCallException.class, e -> assertThat(e.getKind()).isEqualTo(Kind.INVALID_ARGUMENTS));
        assertThatThrownBy(() -> call(functions, "addCharacter", "[1]"))
                .isInstanceOfSatisfying(FunctionCallException.class, e -> assertThat(e.getKind()).isEqualTo(Kind.INVALID_ARGUMENTS));
        assertThat(story.calls).isEmpty();
    }

    @Test
    void exceptionOfTheMethodBecomesAFailedCall() {
        FunctionSet functions = registry.bind(Story.class, story);

        assertThatThrownBy(() -> call(functions, "fail", "{\"reason\":\"no luck\"}"))
                .isInstanceOfSatisfying(FunctionCallException.class, e -> {
                    assertThat(e.getKind()).isEqualTo(Kind.FAILED);
                    assertThat(e.getMessage()).contains("no luck");
                    assertThat(e.getCause()).isInstanceOf(IllegalStateException.class);
                });
    }

    @Test
    void methodRunsOnTheExecutorNotOnTheCaller() {
        FunctionSet functions = registry.bind(Story.class, story);

        call(functions, "addCharacter", "{\"name\":\"Ada\",\"age\":1,\"mood\":\"CALM\"}");

        assertThat(story.threads).containsExactly("function-test");
    }

    @Test
    void continuationFailedFromAnotherThreadIsAFailedCall() {
        FunctionSet functions = registry.bind(Story.class, story);

        assertThatThrownBy(() -> call(functions, "failAsync", "{\"reason\":\"late\"}"))
                .isInstanceOfSatisfying(FunctionCallException.class, e -> {
                    assertThat(e.getKind()).isEqualTo(Kind.FAILED);
                    assertThat(e.getMessage()).contains("late");
                });
    }

    @Test
    void continuationIsNotPartOfTheSchema() {
        assertThat(definition("addCharacter").getParametersSchema().get("properties").fieldNames())
                .toIterable().containsExactly("name", "age", "mood", "traits");
    }

    @Test
    void firstAnswerWins() {
        FunctionSet functions = registry.bind(Other.class, done -> {
            done.complete("first");
            done.complete("second");
            done.fail("third");
        });

        assertThat(call(functions, "ping", "{}")).isEqualTo("first");
    }

    interface Other {
        @LLMFunction
        void ping(FunctionContinuation<String> done);
    }

    @Test
    void setsOfDifferentInterfacesCombine() {
        FunctionSet functions = registry.bind(Story.class, story).and(registry.bind(Other.class, done -> done.complete("pong")));

        assertThat(functions.getDefinitions()).extracting(FunctionDefinition::getName).contains("ping", "clear");
        assertThat(call(functions, "ping", "{}")).isEqualTo("pong");
        assertThatThrownBy(() -> functions.and(registry.bind(Other.class, done -> done.complete("again"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    interface Twice {
        @LLMFunction
        void same(FunctionContinuation<Void> done, int a);

        @LLMFunction(name = "same")
        void other(FunctionContinuation<Void> done, int b);
    }

    interface Nothing {
        void plain();
    }

    interface BadName {
        @LLMFunction(name = "with space")
        void f(FunctionContinuation<Void> done);
    }

    interface Returns {
        @LLMFunction
        String sync(FunctionContinuation<String> done);
    }

    interface NoContinuation {
        @LLMFunction
        void a();
    }

    interface ContinuationNotFirst {
        @LLMFunction
        void a(int x, FunctionContinuation<String> done);
    }

    interface Interface {
        @LLMFunction
        void a(Runnable done);
    }

    @Test
    void unusableInterfacesAreRejected() {
        assertThatThrownBy(() -> registry.definitionsOf(Twice.class)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("same");
        assertThatThrownBy(() -> registry.definitionsOf(Nothing.class)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.definitionsOf(BadName.class)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.definitionsOf(Returns.class)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("void");
        assertThatThrownBy(() -> registry.definitionsOf(NoContinuation.class)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("FunctionContinuation");
        assertThatThrownBy(() -> registry.definitionsOf(ContinuationNotFirst.class)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("first parameter");
        assertThatThrownBy(() -> registry.definitionsOf(Interface.class)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("first parameter");
        assertThatThrownBy(() -> registry.definitionsOf(StoryImpl.class)).isInstanceOf(IllegalArgumentException.class);
    }
}
