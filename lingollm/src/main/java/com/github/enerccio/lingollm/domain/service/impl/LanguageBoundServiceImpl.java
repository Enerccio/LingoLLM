package com.github.enerccio.lingollm.domain.service.impl;

import com.github.enerccio.lingollm.domain.model.LanguageBoundEntity;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.repository.LanguageBoundRepository;
import com.github.enerccio.lingollm.domain.service.LanguageBoundService;
import com.github.enerccio.lingollm.domain.traits.CommonTx;
import com.github.enerccio.lingollm.domain.traits.CommonTxReadOnly;

import com.github.enerccio.lingollm.domain.service.LanguageService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public abstract class LanguageBoundServiceImpl<T extends LanguageBoundEntity, R extends LanguageBoundRepository<T>>
        extends ExtendableServiceImpl<T, R> implements LanguageBoundService<T, R> {

    /** The language profile of the session, a reference: only its id is filled in. */
    @Autowired
    protected Language currentLanguage;

    @Autowired
    private LanguageService languageService;

    @Override
    @CommonTx
    public int softDeleteForLanguage(Language language) throws Exception {
        return getRepository().softDeleteByLanguage(language);
    }

    /**
     * A new object without a language belongs to the language profile of the session.
     */
    @Override
    public T save(T t) throws Exception {
        if (t.getLanguage() == null && t.getId() == null && currentLanguage.getId() != null) {
            t.setLanguage(languageService.find(currentLanguage.getId()));
        }
        return super.save(t);
    }

    @Override
    @CommonTxReadOnly
    public List<T> findAllForUser(Language language) throws Exception {
        return getRepository().findAll(currentUser, language);
    }

    @Override
    @CommonTxReadOnly
    public List<Long> findAllIdsForUser(Language language) throws Exception {
        return getRepository().findAllIds(currentUser, language);
    }

}
