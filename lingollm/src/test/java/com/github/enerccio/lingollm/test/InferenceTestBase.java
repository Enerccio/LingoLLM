package com.github.enerccio.lingollm.test;

import com.github.enerccio.lingollm.domain.model.impl.OpenAICompatible;
import com.github.enerccio.lingollm.test.llm.MockLLMResponse;
import org.junit.jupiter.api.BeforeEach;

import java.time.Duration;

/**
 * Calls an {@code InferenceService} against the mock LLM: a logged in user and an AI pointing to {@link #llm} are
 * ready before each test; the mock answers every completion with {@code "generated"} unless the test programs
 * something else.
 */
public abstract class InferenceTestBase extends LingoLLMTestBase {

    public static final Duration TIMEOUT = Duration.ofSeconds(30);

    protected OpenAICompatible ai;

    @BeforeEach
    void setUpInference() throws Exception {
        login();
        ai = createAI();
        llm.fallback(MockLLMResponse.text("generated"));
    }
}
