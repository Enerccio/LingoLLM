package com.github.enerccio.lingollm.domain.service.impl;

import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.repository.LanguageRepository;
import com.github.enerccio.lingollm.domain.service.FreeChatMessageService;
import com.github.enerccio.lingollm.domain.service.FreeChatService;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.github.enerccio.lingollm.domain.traits.CommonTx;
import org.springframework.beans.factory.annotation.Autowired;

public class LanguageServiceImpl extends ExtendableServiceImpl<Language, LanguageRepository> implements LanguageService {

    @Autowired
    private FreeChatService chatService;

    @Autowired
    private FreeChatMessageService messageService;

    /**
     * A soft delete takes everything that belongs to the profile with it (one bulk update per kind, the rows are not
     * loaded).
     */
    @Override
    @CommonTx
    public Language delete(Language language, boolean hard) throws Exception {
        if (!hard && language != null && language.getId() != null) {
            messageService.softDeleteForLanguage(language);
            chatService.softDeleteForLanguage(language);
        }
        return super.delete(language, hard);
    }

}
