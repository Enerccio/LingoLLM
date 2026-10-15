package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.factories.TestQuestionGroupFactory;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionGroup;

import java.util.Collection;

/**
 * The {@link TestQuestionGroupFactory}s by their class name, which is what {@link TestQuestionGroup#getFactory()}
 * stores. Works like {@link TestQuestionFactoryRegistry}: built-in factories are loaded by their name and cached,
 * extensions register theirs on load and {@linkplain #unregister unregister} them on unload.
 */
public interface TestQuestionGroupFactoryRegistry {

    /**
     * @return the factory of the class name, null when the application doesn't know it (an extension that is not
     * loaded, a removed class): the group is then listed but can't be shown
     */
    TestQuestionGroupFactory getFactory(String className);

    /**
     * @return the factory of the group, null when it has none or it is unknown
     */
    TestQuestionGroupFactory getFactory(TestQuestionGroup group);

    /**
     * Makes the factory available under the name of its class, replacing the one registered before.
     */
    void register(TestQuestionGroupFactory factory);

    /**
     * Forgets the factory of the class, e.g. when its extension is unloaded.
     */
    void unregister(Class<? extends TestQuestionGroupFactory> type);

    /**
     * @return the factories known so far (registered or already used)
     */
    Collection<TestQuestionGroupFactory> getFactories();

}
