package com.github.enerccio.lingollm.domain.service.function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.enerccio.lingollm.domain.service.function.FunctionCallException.Kind;

import java.lang.invoke.MethodHandle;
import java.util.List;

/**
 * One {@link com.github.enerccio.lingollm.domain.traits.LLMFunction} method, compiled: the JSON schema of its
 * parameters and the method handle. It does not know the object it is called on, so it is created once per interface
 * and shared ({@link com.github.enerccio.lingollm.domain.service.FunctionRegistry}); {@link FunctionSet} binds it
 * to an implementation.
 */
public final class FunctionDefinition {

    /** A parameter of the method: the name of its argument and what the argument is parsed into. */
    record Parameter(String name, JavaType type, boolean required) {

        Object defaultValue() {
            Class<?> raw = type.getRawClass();
            if (!raw.isPrimitive()) {
                return null;
            }
            if (raw == boolean.class) {
                return false;
            }
            if (raw == char.class) {
                return (char) 0;
            }
            // numbers: the generic invocation converts the Integer to the parameter's width
            return 0;
        }
    }

    private final String name;
    private final String description;
    private final ObjectNode parametersSchema;
    private final List<Parameter> parameters;
    private final MethodHandle handle;

    /** @param handle takes the target, the continuation and then the arguments as Objects, see {@link FunctionCompiler} */
    FunctionDefinition(String name, String description, ObjectNode parametersSchema, List<Parameter> parameters,
                       MethodHandle handle) {
        this.name = name;
        this.description = description;
        this.parametersSchema = parametersSchema;
        this.parameters = List.copyOf(parameters);
        this.handle = handle;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /**
     * JSON schema ({@code type: object}) of the arguments. Shared by all users of the definition, do not modify.
     */
    public ObjectNode getParametersSchema() {
        return parametersSchema;
    }

    /**
     * Starts the method on the target with the continuation and the arguments parsed from the JSON object the model
     * wrote. The method answers through the continuation, whenever it likes; this returns when the method does.
     *
     * @throws FunctionCallException when the arguments do not fit or the method threw
     */
    void invoke(Object target, FunctionContinuation<?> continuation, String argumentsJson) {
        Object[] call = new Object[parameters.size() + 2];
        call[0] = target;
        call[1] = continuation;
        JsonNode arguments = parseArguments(argumentsJson);
        for (int i = 0; i < parameters.size(); i++) {
            call[i + 2] = readArgument(parameters.get(i), arguments);
        }
        try {
            handle.invoke(call);
        } catch (Error e) {
            throw e;
        } catch (Throwable e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new FunctionCallException(Kind.FAILED, "Function " + name + " failed: " + message, e);
        }
    }

    /** The text the model gets for the value a function completed with. */
    static String render(Object result) {
        if (result == null) {
            return "OK";
        }
        if (result instanceof CharSequence text) {
            return text.toString();
        }
        try {
            return FunctionJson.MAPPER.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new FunctionCallException(Kind.FAILED, "The result of the function can not be written as JSON: "
                    + e.getOriginalMessage(), e);
        }
    }

    private ObjectNode parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return FunctionJson.MAPPER.createObjectNode();
        }
        try {
            JsonNode node = FunctionJson.MAPPER.readTree(argumentsJson);
            if (node instanceof ObjectNode object) {
                return object;
            }
            if (node == null || node.isNull() || node.isMissingNode()) {
                return FunctionJson.MAPPER.createObjectNode();
            }
            throw new FunctionCallException(Kind.INVALID_ARGUMENTS,
                    "The arguments of " + name + " must be a JSON object with the parameters of the function");
        } catch (JsonProcessingException e) {
            throw new FunctionCallException(Kind.INVALID_ARGUMENTS,
                    "The arguments of " + name + " are not valid JSON: " + e.getOriginalMessage(), e);
        }
    }

    private Object readArgument(Parameter parameter, JsonNode arguments) {
        JsonNode node = arguments.get(parameter.name());
        if (node == null || node.isNull()) {
            if (parameter.required()) {
                throw new FunctionCallException(Kind.INVALID_ARGUMENTS,
                        "Missing required parameter '" + parameter.name() + "' of " + name);
            }
            return parameter.defaultValue();
        }
        try {
            return FunctionJson.MAPPER.readerFor(parameter.type()).readValue(node);
        } catch (java.io.IOException | RuntimeException e) {
            String reason = e instanceof JsonProcessingException json ? json.getOriginalMessage() : e.getMessage();
            throw new FunctionCallException(Kind.INVALID_ARGUMENTS,
                    "Parameter '" + parameter.name() + "' of " + name + " is not valid: " + reason, e);
        }
    }

}
