package com.github.enerccio.lingollm.domain.service.dto;

import java.util.ArrayList;
import java.util.List;

public class LLMChatMessage {

    private LLMRole role;
    private String content;
    private List<LLMAttachment> attachments = new ArrayList<>();
    // null when there are none, so that the serialized payload of a plain message stays small
    private List<LLMToolCall> toolCalls;
    private String toolCallId;

    public LLMRole getRole() {
        return role;
    }

    public void setRole(LLMRole role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    /** Media sent along with the text; only providers/models that support them use them. Never null. */
    public List<LLMAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<LLMAttachment> attachments) {
        this.attachments = attachments != null ? attachments : new ArrayList<>();
    }

    public LLMChatMessage addAttachment(LLMAttachment attachment) {
        attachments.add(attachment);
        return this;
    }

    public boolean hasAttachments() {
        return !attachments.isEmpty();
    }

    /** Functions the assistant called with this message, in the order they are answered. Never null. */
    public List<LLMToolCall> getToolCalls() {
        return toolCalls != null ? toolCalls : List.of();
    }

    public void setToolCalls(List<LLMToolCall> toolCalls) {
        this.toolCalls = toolCalls != null && !toolCalls.isEmpty() ? new ArrayList<>(toolCalls) : null;
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /** For a {@link LLMRole#TOOL} message: the {@link LLMToolCall#getId() id} of the call this is the result of. */
    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public static LLMChatMessage of(LLMRole role, String content) {
        LLMChatMessage message = new LLMChatMessage();
        message.role = role;
        message.content = content;
        return message;
    }

    /** Assistant message that calls functions; the content is the text it wrote before the calls, may be null. */
    public static LLMChatMessage ofToolCalls(String content, List<LLMToolCall> toolCalls) {
        LLMChatMessage message = of(LLMRole.ASSISTANT, content);
        message.setToolCalls(toolCalls);
        return message;
    }

    /** What a called function returned (or the error it ended with). */
    public static LLMChatMessage ofToolResult(String toolCallId, String result) {
        LLMChatMessage message = of(LLMRole.TOOL, result);
        message.toolCallId = toolCallId;
        return message;
    }

}
