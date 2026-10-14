package com.github.enerccio.lingollm.domain.repository;

import com.github.enerccio.lingollm.domain.model.impl.FreeChat;
import com.github.enerccio.lingollm.domain.model.impl.FreeChatMessage;

import java.util.List;

public interface FreeChatMessageRepository extends LanguageBoundRepository<FreeChatMessage> {

    /**
     * @return the messages of the chat, oldest first
     */
    List<FreeChatMessage> findByChat(FreeChat chat) throws Exception;

    /**
     * Soft deletes the messages of the chat with one bulk update.
     */
    int softDeleteByChat(FreeChat chat) throws Exception;

}
