package com.github.enerccio.lingollm.domain.service.impl;

import com.github.enerccio.lingollm.domain.model.impl.FreeChat;
import com.github.enerccio.lingollm.domain.model.impl.FreeChatMessage;
import com.github.enerccio.lingollm.domain.repository.FreeChatMessageRepository;
import com.github.enerccio.lingollm.domain.service.FreeChatMessageService;
import com.github.enerccio.lingollm.domain.traits.CommonTx;
import com.github.enerccio.lingollm.domain.traits.CommonTxReadOnly;

import java.util.List;

public class FreeChatMessageServiceImpl extends LanguageBoundServiceImpl<FreeChatMessage, FreeChatMessageRepository>
        implements FreeChatMessageService {

    @Override
    @CommonTx
    public int softDeleteForChat(FreeChat chat) throws Exception {
        return getRepository().softDeleteByChat(chat);
    }

    @Override
    @CommonTxReadOnly
    public List<FreeChatMessage> findByChat(FreeChat chat) throws Exception {
        if (chat == null || chat.getOwner() == null || !chat.getOwner().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Not a chat of the current user");
        }
        return getRepository().findByChat(chat);
    }

}
