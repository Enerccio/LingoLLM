package com.github.enerccio.lingollm.domain.service.impl;

import com.github.enerccio.lingollm.domain.model.impl.FreeChat;
import com.github.enerccio.lingollm.domain.repository.FreeChatRepository;
import com.github.enerccio.lingollm.domain.service.FreeChatMessageService;
import com.github.enerccio.lingollm.domain.service.FreeChatService;
import com.github.enerccio.lingollm.domain.traits.CommonTx;
import org.springframework.beans.factory.annotation.Autowired;

public class FreeChatServiceImpl extends LanguageBoundServiceImpl<FreeChat, FreeChatRepository> implements FreeChatService {

    @Autowired
    private FreeChatMessageService messageService;

    /**
     * A soft delete takes the messages of the chat with it.
     */
    @Override
    @CommonTx
    public FreeChat delete(FreeChat chat, boolean hard) throws Exception {
        if (!hard && chat != null && chat.getId() != null) {
            messageService.softDeleteForChat(chat);
        }
        return super.delete(chat, hard);
    }

}
