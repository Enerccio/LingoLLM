package com.github.enerccio.lingollm.domain.repository.impl;

import com.github.enerccio.lingollm.domain.model.impl.FreeChat;
import com.github.enerccio.lingollm.domain.repository.FreeChatRepository;

public class JpaFreeChatRepository extends JpaLanguageBoundRepository<FreeChat> implements FreeChatRepository {

    @Override
    protected Class<FreeChat> getEntityClass() {
        return FreeChat.class;
    }

}
