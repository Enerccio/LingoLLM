package com.github.enerccio.lingollm.domain.service.impl;

import com.github.enerccio.lingollm.domain.factories.TestQuestionGroupFactory;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionGroup;
import com.github.enerccio.lingollm.domain.service.TestQuestionGroupFactoryRegistry;

import java.util.Collection;

public class TestQuestionGroupFactoryRegistryImpl implements TestQuestionGroupFactoryRegistry {

    private final ClassNameRegistry<TestQuestionGroupFactory> registry =
            new ClassNameRegistry<>(TestQuestionGroupFactory.class);

    @Override
    public TestQuestionGroupFactory getFactory(String className) {
        return registry.get(className);
    }

    @Override
    public TestQuestionGroupFactory getFactory(TestQuestionGroup group) {
        return group == null ? null : getFactory(group.getFactory());
    }

    @Override
    public void register(TestQuestionGroupFactory factory) {
        registry.register(factory);
    }

    @Override
    public void unregister(Class<? extends TestQuestionGroupFactory> type) {
        registry.unregister(type);
    }

    @Override
    public Collection<TestQuestionGroupFactory> getFactories() {
        return registry.all();
    }

}
