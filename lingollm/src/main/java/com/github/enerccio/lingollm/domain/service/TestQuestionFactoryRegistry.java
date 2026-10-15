package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.factories.TestQuestionFactory;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;

import java.util.Collection;

/**
 * The {@link TestQuestionFactory}s by their class name, which is what {@link TestQuestion#getFactory()} stores. A
 * factory is stateless, so one instance is kept for every class.
 * <p>
 * Built-in factories need no registration: a class name that is not registered is loaded by the application, if it is
 * a {@link TestQuestionFactory} with a public constructor without arguments, and cached. Extensions register their
 * factories on load and {@linkplain #unregister unregister} them on unload, the application can't see their classes.
 */
public interface TestQuestionFactoryRegistry {

    /**
     * @return the factory of the class name, null when the application doesn't know it (an extension that is not
     * loaded, a removed class): the question is then listed but can't be shown
     */
    TestQuestionFactory getFactory(String className);

    /**
     * @return the factory of the question, null when it has none or it is unknown
     */
    TestQuestionFactory getFactory(TestQuestion question);

    /**
     * Makes the factory available under the name of its class, replacing the one registered before.
     */
    void register(TestQuestionFactory factory);

    /**
     * Forgets the factory of the class, e.g. when its extension is unloaded.
     */
    void unregister(Class<? extends TestQuestionFactory> type);

    /**
     * @return the factories known so far (registered or already used)
     */
    Collection<TestQuestionFactory> getFactories();

}
