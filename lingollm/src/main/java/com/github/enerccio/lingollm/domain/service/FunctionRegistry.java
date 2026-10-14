package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.service.function.FunctionDefinition;
import com.github.enerccio.lingollm.domain.service.function.FunctionSet;

import java.util.List;

/**
 * Compiled functions the LLM can call, one entry per interface so that the schema generation and the reflection
 * happen once however often the interface is bound. Get it from {@link InferenceServices#getFunctionRegistry()}.
 * <p>
 * The functions are the {@link com.github.enerccio.lingollm.domain.traits.LLMFunction} methods of an interface.
 * An entry lives as long as the interface class does, so interfaces of an unloaded extension are released.
 */
public interface FunctionRegistry {

    /**
     * The definitions (names, descriptions, JSON schemas) of an interface, compiled on first use.
     *
     * @throws IllegalArgumentException when the type is not an interface or its functions are not usable
     */
    List<FunctionDefinition> definitionsOf(Class<?> type);

    /**
     * Binds the functions of the interface to an implementation, to be passed to
     * {@link InferenceService#stream(List, FunctionSet, InferenceService.InferenceAsyncCallback)}.
     * The implementation is what the model's calls end in; make it a short-lived object of the request if it needs to
     * know the user, story or session it works for.
     */
    <T> FunctionSet bind(Class<T> type, T implementation);

}
