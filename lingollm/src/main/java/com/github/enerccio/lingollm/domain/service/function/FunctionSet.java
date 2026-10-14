package com.github.enerccio.lingollm.domain.service.function;

import com.github.enerccio.lingollm.domain.service.function.FunctionCallException.Kind;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/**
 * Functions the model may call in one request: {@link FunctionDefinition}s bound to the objects that implement them.
 * Cheap to create and immutable; the expensive part is in the definitions, which the registry caches.
 * <pre>
 * FunctionSet functions = inferenceServices.getFunctionRegistry().bind(Weather.class, new WeatherImpl());
 * inferenceService.stream(payload, protocol, functions, callback);
 * </pre>
 */
public final class FunctionSet {

    private static final FunctionSet EMPTY = new FunctionSet(Map.of());

    private record Binding(FunctionDefinition definition, Object target) {}

    private final Map<String, Binding> functions;

    private FunctionSet(Map<String, Binding> functions) {
        this.functions = functions;
    }

    public static FunctionSet empty() {
        return EMPTY;
    }

    /** Binds the definitions of an interface to its implementation. */
    public static FunctionSet bind(Collection<FunctionDefinition> definitions, Object target) {
        Map<String, Binding> bindings = new LinkedHashMap<>();
        for (FunctionDefinition definition : definitions) {
            bindings.put(definition.getName(), new Binding(definition, target));
        }
        return new FunctionSet(bindings);
    }

    /**
     * @throws IllegalArgumentException when both have a function of the same name
     */
    public FunctionSet and(FunctionSet other) {
        if (other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        Map<String, Binding> bindings = new LinkedHashMap<>(functions);
        for (Map.Entry<String, Binding> binding : other.functions.entrySet()) {
            if (bindings.putIfAbsent(binding.getKey(), binding.getValue()) != null) {
                throw new IllegalArgumentException("Function " + binding.getKey() + " is bound twice");
            }
        }
        return new FunctionSet(bindings);
    }

    public boolean isEmpty() {
        return functions.isEmpty();
    }

    public boolean contains(String name) {
        return functions.containsKey(name);
    }

    /** The functions in the order they were bound. */
    public List<FunctionDefinition> getDefinitions() {
        List<FunctionDefinition> definitions = new ArrayList<>();
        for (Binding binding : functions.values()) {
            definitions.add(binding.definition());
        }
        return definitions;
    }

    /**
     * Calls a function with the arguments the model wrote, asynchronously: the method runs on the executor (argument
     * parsing included) and gets a {@link FunctionContinuation} to answer with, from whichever thread it likes.
     * Nothing is thrown; every failure is the exception the returned future completes with.
     *
     * @param argumentsJson JSON object with the arguments by parameter name
     * @param executor      runs the call; wrap it to carry the request scope of the caller
     * @param cancelled     asked by {@link FunctionContinuation#isCancelled()}
     * @return the text for the model: what the method completed with (a string as it is, anything else as JSON, null
     * as "OK"). Completes exceptionally with a {@link FunctionCallException} when the function is unknown, the
     * arguments do not fit, the method threw or failed the continuation.
     */
    public CompletableFuture<String> call(String name, String argumentsJson, Executor executor, BooleanSupplier cancelled) {
        CompletableFuture<String> result = new CompletableFuture<>();
        Binding binding = functions.get(name);
        if (binding == null) {
            result.completeExceptionally(new FunctionCallException(Kind.UNKNOWN_FUNCTION, "Unknown function " + name
                    + ", available functions: " + String.join(", ", functions.keySet())));
            return result;
        }
        PendingCall continuation = new PendingCall(binding.definition().getName(), result, cancelled);
        try {
            executor.execute(() -> {
                try {
                    binding.definition().invoke(binding.target(), continuation, argumentsJson);
                } catch (FunctionCallException e) {
                    result.completeExceptionally(e);
                } catch (Throwable e) {
                    result.completeExceptionally(new FunctionCallException(Kind.FAILED,
                            "Function " + name + " failed: " + e, e));
                }
            });
        } catch (RuntimeException e) {
            result.completeExceptionally(new FunctionCallException(Kind.FAILED, "Function " + name + " could not be started: " + e, e));
        }
        return result;
    }

    /** The continuation of one call: finishes the future the caller waits on, once. */
    private record PendingCall(String name, CompletableFuture<String> result, BooleanSupplier cancelled)
            implements FunctionContinuation<Object> {

        @Override
        public void complete(Object value) {
            try {
                result.complete(FunctionDefinition.render(value));
            } catch (FunctionCallException e) {
                result.completeExceptionally(e);
            }
        }

        @Override
        public void fail(String message) {
            result.completeExceptionally(new FunctionCallException(Kind.FAILED, "Function " + name + " failed: " + message));
        }

        @Override
        public void fail(Throwable error) {
            if (error instanceof FunctionCallException e) {
                result.completeExceptionally(e);
                return;
            }
            String message = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
            result.completeExceptionally(new FunctionCallException(Kind.FAILED, "Function " + name + " failed: " + message, error));
        }

        @Override
        public boolean isCancelled() {
            return cancelled.getAsBoolean();
        }
    }

}
