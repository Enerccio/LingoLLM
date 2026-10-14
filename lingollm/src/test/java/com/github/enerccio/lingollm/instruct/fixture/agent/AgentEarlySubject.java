package com.github.enerccio.lingollm.instruct.fixture.agent;

import com.github.enerccio.lingollm.domain.traits.Extendable;

/**
 * Loaded before the agent is installed - instrumented by retransformation.
 */
@Extendable
public class AgentEarlySubject {

    public String greet(String name) {
        StringBuilder greeting = new StringBuilder("hello ");
        greeting.append(name);
        return greeting.toString();
    }

    public int divide(int a, int b) {
        return a / b;
    }
}
