package com.github.enerccio.lingollm.domain.service.function;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.enerccio.lingollm.domain.traits.LLMFunction;
import com.github.enerccio.lingollm.domain.traits.LLMParam;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Turns the {@link LLMFunction} methods of an interface into {@link FunctionDefinition}s: the schema of each method's
 * parameters, the types its arguments are parsed into and a method handle to call it. This is the expensive part, so
 * the registry does it once per interface.
 */
public final class FunctionCompiler {

    /** What the providers accept as a function name. */
    private static final Pattern VALID_NAME = Pattern.compile("[a-zA-Z0-9_-]{1,64}");

    private static final SchemaGenerator SCHEMAS = new SchemaGenerator(
            new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                    .with(new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED, JacksonOption.RESPECT_JSONPROPERTY_ORDER))
                    // every parameter gets its own complete schema, the function schema embeds them
                    .with(Option.INLINE_ALL_SCHEMAS)
                    .without(Option.SCHEMA_VERSION_INDICATOR)
                    .build());

    private FunctionCompiler() {
    }

    /**
     * @throws IllegalArgumentException when the type is not an interface, has no {@link LLMFunction} method or a
     *                                  function is not usable (name taken, parameter without a name, ...)
     */
    public static List<FunctionDefinition> compile(Class<?> type) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException(type.getName() + " is not an interface");
        }

        List<Method> methods = new ArrayList<>();
        for (Method method : type.getMethods()) {
            if (method.isAnnotationPresent(LLMFunction.class) && !method.isBridge() && !method.isSynthetic()) {
                if (Modifier.isStatic(method.getModifiers())) {
                    throw new IllegalArgumentException("@LLMFunction method " + method + " is static");
                }
                methods.add(method);
            }
        }
        if (methods.isEmpty()) {
            throw new IllegalArgumentException(type.getName() + " has no @LLMFunction method");
        }
        methods.sort(Comparator.comparing(Method::getName).thenComparing(Method::toString));

        Map<String, FunctionDefinition> definitions = new LinkedHashMap<>();
        for (Method method : methods) {
            FunctionDefinition definition = compile(method);
            if (definitions.putIfAbsent(definition.getName(), definition) != null) {
                throw new IllegalArgumentException("Function name " + definition.getName() + " is used twice in "
                        + type.getName() + ", give the overloaded methods different @LLMFunction names");
            }
        }
        return List.copyOf(definitions.values());
    }

    private static FunctionDefinition compile(Method method) {
        LLMFunction annotation = method.getAnnotation(LLMFunction.class);
        String name = annotation.name().isEmpty() ? method.getName() : annotation.name();
        if (!VALID_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Function name '" + name + "' of " + method
                    + " must be 1-64 letters, digits, '_' or '-'");
        }

        ObjectNode properties = FunctionJson.MAPPER.createObjectNode();
        ObjectNode definitions = FunctionJson.MAPPER.createObjectNode();
        ArrayNode required = FunctionJson.MAPPER.createArrayNode();
        List<FunctionDefinition.Parameter> parameters = new ArrayList<>();

        requireAsync(method);
        java.lang.reflect.Parameter[] declared = method.getParameters();
        // the first one is the continuation, the model does not know it
        for (int p = 1; p < declared.length; p++) {
            java.lang.reflect.Parameter parameter = declared[p];
            LLMParam param = parameter.getAnnotation(LLMParam.class);
            String parameterName = param != null && !param.name().isEmpty() ? param.name() : null;
            if (parameterName == null) {
                if (!parameter.isNamePresent()) {
                    throw new IllegalArgumentException("Parameter " + parameter + " of " + method + " has no name: compile with"
                            + " -parameters or name it with @LLMParam(name = ...)");
                }
                parameterName = parameter.getName();
            }
            if (properties.has(parameterName)) {
                throw new IllegalArgumentException("Parameter name " + parameterName + " is used twice in " + method);
            }

            Type genericType = parameter.getParameterizedType();
            JavaType javaType = FunctionJson.MAPPER.getTypeFactory().constructType(genericType);
            ObjectNode schema = schemaOf(javaType, genericType);
            hoistDefinitions(schema, definitions);
            if (param != null && !param.description().isEmpty()) {
                schema.put("description", param.description());
            }
            properties.set(parameterName, schema);

            boolean isRequired = param == null || param.required();
            if (isRequired) {
                required.add(parameterName);
            }
            parameters.add(new FunctionDefinition.Parameter(parameterName, javaType, isRequired));
        }

        ObjectNode root = FunctionJson.MAPPER.createObjectNode();
        root.put("type", "object");
        root.set("properties", properties);
        if (!required.isEmpty()) {
            root.set("required", required);
        }
        root.put("additionalProperties", false);
        if (!definitions.isEmpty()) {
            root.set("$defs", definitions);
        }

        return new FunctionDefinition(name, annotation.description(), root, parameters, handleOf(method));
    }

    /** A function is asynchronous: {@code void name(FunctionContinuation<R> continuation, arguments...)}. */
    private static void requireAsync(Method method) {
        if (method.getReturnType() != void.class) {
            throw new IllegalArgumentException("@LLMFunction method " + method + " must return void, it answers through"
                    + " its FunctionContinuation");
        }
        if (method.getParameterCount() == 0 || method.getParameterTypes()[0] != FunctionContinuation.class) {
            throw new IllegalArgumentException("@LLMFunction method " + method + " must take a FunctionContinuation as"
                    + " its first parameter");
        }
        if (method.getParameters()[0].isAnnotationPresent(LLMParam.class)) {
            throw new IllegalArgumentException("The FunctionContinuation of " + method + " is not a parameter of the"
                    + " model, it can not be an @LLMParam");
        }
    }

    private static ObjectNode schemaOf(JavaType javaType, Type genericType) {
        if (JsonNode.class.isAssignableFrom(javaType.getRawClass())) {
            // any JSON; the generator would describe the node classes instead
            return FunctionJson.MAPPER.createObjectNode();
        }
        return SCHEMAS.generateSchema(genericType);
    }

    /**
     * The only $refs left after inlining point to recursive types, in the {@code $defs} of the schema they are in.
     * Moving the definitions to the root of the function schema keeps those references valid.
     */
    private static void hoistDefinitions(ObjectNode schema, ObjectNode target) {
        JsonNode defs = schema.remove("$defs");
        if (defs != null) {
            for (Iterator<Map.Entry<String, JsonNode>> it = defs.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> definition = it.next();
                target.set(definition.getKey(), definition.getValue());
            }
        }
    }

    /**
     * {@code (target, continuation, arguments...) -> void} with everything as Objects, so that the call needs no reflection
     * and no boxing of the argument array into varargs.
     */
    private static MethodHandle handleOf(Method method) {
        try {
            method.setAccessible(true);
            int arity = method.getParameterCount() + 1;
            return MethodHandles.lookup().unreflect(method)
                    .asType(MethodType.genericMethodType(arity).changeReturnType(void.class))
                    .asSpreader(Object[].class, arity);
        } catch (IllegalAccessException | RuntimeException e) {
            throw new IllegalArgumentException("Can not call " + method, e);
        }
    }

}
