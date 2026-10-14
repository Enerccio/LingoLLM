package com.github.enerccio.lingollm.instruct.fixture.agent;

/**
 * Not {@code @Extendable} - must stay untouched by the agent.
 */
public class AgentPlainSubject {

    public String greet(String name) {
        return "hi " + name;
    }
}
