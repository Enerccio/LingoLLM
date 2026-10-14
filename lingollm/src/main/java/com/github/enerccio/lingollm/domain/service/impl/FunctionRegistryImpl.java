package com.github.enerccio.lingollm.domain.service.impl;

import com.github.enerccio.lingollm.domain.service.FunctionRegistry;
import com.github.enerccio.lingollm.domain.service.function.FunctionCompiler;
import com.github.enerccio.lingollm.domain.service.function.FunctionDefinition;
import com.github.enerccio.lingollm.domain.service.function.FunctionSet;

import java.util.List;

public class FunctionRegistryImpl implements FunctionRegistry {

    // stored in the class itself, so the entry of an extension's interface goes away with the extension
    private final ClassValue<List<FunctionDefinition>> compiled = new ClassValue<>() {
        @Override
        protected List<FunctionDefinition> computeValue(Class<?> type) {
            return FunctionCompiler.compile(type);
        }
    };

    @Override
    public List<FunctionDefinition> definitionsOf(Class<?> type) {
        return compiled.get(type);
    }

    @Override
    public <T> FunctionSet bind(Class<T> type, T implementation) {
        if (implementation == null) {
            throw new IllegalArgumentException("No implementation of " + type.getName());
        }
        return FunctionSet.bind(definitionsOf(type), implementation);
    }

}
