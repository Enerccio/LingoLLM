package com.github.enerccio.lingollm.domain.service.impl;

import com.github.enerccio.lingollm.domain.factories.TestQuestionFactory;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.domain.service.TestQuestionFactoryRegistry;

import java.util.Collection;

public class TestQuestionFactoryRegistryImpl implements TestQuestionFactoryRegistry {

    private final ClassNameRegistry<TestQuestionFactory> registry = new ClassNameRegistry<>(TestQuestionFactory.class);

    @Override
    public TestQuestionFactory getFactory(String className) {
        return registry.get(className);
    }

    @Override
    public TestQuestionFactory getFactory(TestQuestion question) {
        return question == null ? null : getFactory(question.getFactory());
    }

    @Override
    public void register(TestQuestionFactory factory) {
        registry.register(factory);
    }

    @Override
    public void unregister(Class<? extends TestQuestionFactory> type) {
        registry.unregister(type);
    }

    @Override
    public Collection<TestQuestionFactory> getFactories() {
        return registry.all();
    }

}
