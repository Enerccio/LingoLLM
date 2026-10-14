package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.model.impl.FreeChat;
import com.github.enerccio.lingollm.domain.model.impl.FreeChatMessage;
import com.github.enerccio.lingollm.domain.repository.FreeChatMessageRepository;

import java.util.List;

public interface FreeChatMessageService extends LanguageBoundService<FreeChatMessage, FreeChatMessageRepository> {

    /**
     * @return the messages of the chat of the current user, oldest first
     */
    List<FreeChatMessage> findByChat(FreeChat chat) throws Exception;

    /**
     * Soft deletes the messages of the chat (used when the chat is deleted).
     */
    int softDeleteForChat(FreeChat chat) throws Exception;

}
