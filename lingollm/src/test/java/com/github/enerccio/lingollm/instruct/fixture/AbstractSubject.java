package com.github.enerccio.lingollm.instruct.fixture;

/**
 * Abstract and concrete methods side by side - abstract methods have no code to instrument.
 */
public abstract class AbstractSubject {

    public abstract String name();

    public String greet() {
        return "hello " + name();
    }
}
