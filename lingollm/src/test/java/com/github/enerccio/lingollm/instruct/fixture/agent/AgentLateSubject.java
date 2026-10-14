package com.github.enerccio.lingollm.instruct.fixture.agent;

import com.github.enerccio.lingollm.domain.traits.Extendable;

import java.util.ArrayList;
import java.util.List;

/**
 * Loaded only after the agent is installed - instrumented on class load.
 */
@Extendable
public class AgentLateSubject {

    public List<String> build(String first, long count) {
        List<String> items = new ArrayList<>();
        for (long i = 0; i < count; i++) {
            items.add(first + i);
        }
        return items;
    }
}
