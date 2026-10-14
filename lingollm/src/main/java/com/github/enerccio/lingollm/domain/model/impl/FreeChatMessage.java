package com.github.enerccio.lingollm.domain.model.impl;

import com.github.enerccio.lingollm.domain.model.ImageAttachment;
import com.github.enerccio.lingollm.domain.model.LanguageBoundEntity;
import com.github.enerccio.lingollm.domain.traits.CleanupReference;
import com.github.enerccio.lingollm.domain.traits.CleanupReference.Policy;
import com.github.enerccio.lingollm.domain.traits.ExtendedAttribute;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jakarta.persistence.*;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * A message of a {@link FreeChat}. The messages of a chat form a tree like the story parts of Marginalia: a message
 * follows its {@link #getParent() parent}, a message without one starts the chat. Attachments are loose links, the
 * message only keeps the uuid of the {@link Resource} (see {@link ImageAttachment}), so a resource can be deleted
 * without touching the message.
 */
@Entity
@Table(name = "free_chat_messages")
public class FreeChatMessage extends LanguageBoundEntity {

    private static final Gson GSON = new Gson();
    private static final Type ATTACHMENTS_TYPE = new TypeToken<List<ImageAttachment>>() {
    }.getType();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parentChat_id")
    @CleanupReference(Policy.OWNED_BY)
    private FreeChat parentChat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @CleanupReference(Policy.OWNED_BY)
    private FreeChatMessage parent;

    @ExtendedAttribute
    @Transient
    private String contents;

    // JSON array of ImageAttachment, extended attributes are plain strings
    @ExtendedAttribute
    @Transient
    private String attachments;

    public FreeChat getParentChat() {
        return parentChat;
    }

    public void setParentChat(FreeChat parentChat) {
        this.parentChat = parentChat;
    }

    /**
     * @return the message this one answers, null for the first message of the chat
     */
    public FreeChatMessage getParent() {
        return parent;
    }

    public void setParent(FreeChatMessage parent) {
        this.parent = parent;
    }

    public String getContents() {
        return contents;
    }

    public void setContents(String contents) {
        this.contents = contents;
    }

    /**
     * @return attachments of the message in the order they are shown, never null
     */
    public List<ImageAttachment> getAttachments() {
        if (attachments == null || attachments.isBlank()) {
            return new ArrayList<>();
        }
        List<ImageAttachment> list = GSON.fromJson(attachments, ATTACHMENTS_TYPE);
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    public void setAttachments(List<ImageAttachment> attachments) {
        this.attachments = attachments == null || attachments.isEmpty() ? null : GSON.toJson(attachments, ATTACHMENTS_TYPE);
    }
}
