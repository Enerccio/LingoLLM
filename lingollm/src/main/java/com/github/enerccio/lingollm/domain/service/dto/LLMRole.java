package com.github.enerccio.lingollm.domain.service.dto;

public enum LLMRole {

    SYSTEM, ASSISTANT, USER,
    /** Result of a function the assistant called, see {@link LLMChatMessage#ofToolResult}. */
    TOOL

}
