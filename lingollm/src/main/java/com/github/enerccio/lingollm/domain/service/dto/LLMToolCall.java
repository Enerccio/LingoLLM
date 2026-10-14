package com.github.enerccio.lingollm.domain.service.dto;

/**
 * A call of a function the model asked for in an {@link LLMChatMessage} of the {@link LLMRole#ASSISTANT}. The result
 * goes back as a {@link LLMRole#TOOL} message with the same {@link #getId() id}.
 */
public class LLMToolCall {

    private String id;
    private String name;
    private String arguments;

    /** Identifies the call, the result message refers to it. */
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** The arguments as the JSON object the model wrote; never null. */
    public String getArguments() {
        return arguments != null ? arguments : "{}";
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }

    public static LLMToolCall of(String id, String name, String arguments) {
        LLMToolCall call = new LLMToolCall();
        call.id = id;
        call.name = name;
        call.arguments = arguments;
        return call;
    }

}
