package com.github.enerccio.lingollm.domain.collections;

/**
 * Who judged an answer: the server compared it with the stored answers, or the model checked it.
 */
public enum GradedBy {
    SERVER,
    LLM
}
