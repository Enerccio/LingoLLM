package com.github.enerccio.lingollm.domain.repository.impl;

import com.github.enerccio.lingollm.domain.model.impl.FreeChat;
import com.github.enerccio.lingollm.domain.model.impl.FreeChatMessage;
import com.github.enerccio.lingollm.domain.repository.FreeChatMessageRepository;

import java.util.Date;
import java.util.List;

public class JpaFreeChatMessageRepository extends JpaLanguageBoundRepository<FreeChatMessage> implements FreeChatMessageRepository {

    @Override
    protected Class<FreeChatMessage> getEntityClass() {
        return FreeChatMessage.class;
    }

    @Override
    public int softDeleteByChat(FreeChat chat) {
        if (chat == null || chat.getId() == null) {
            return 0;
        }
        return entityManager.createQuery("UPDATE FreeChatMessage e SET e.deleted = true, e.modification = :now"
                        + " WHERE e.parentChat.id = :chat AND e.deleted = false")
                .setParameter("now", new Date())
                .setParameter("chat", chat.getId())
                .executeUpdate();
    }

    @Override
    public List<FreeChatMessage> findByChat(FreeChat chat) throws Exception {
        List<FreeChatMessage> messages = entityManager.createQuery("SELECT e FROM FreeChatMessage e WHERE e.deleted = false"
                        + " AND e.parentChat.id = :chat ORDER BY e.creation, e.id", FreeChatMessage.class)
                .setParameter("chat", chat.getId())
                .getResultList();
        for (FreeChatMessage message : messages) {
            hydrate(message);
        }
        return messages;
    }

}
