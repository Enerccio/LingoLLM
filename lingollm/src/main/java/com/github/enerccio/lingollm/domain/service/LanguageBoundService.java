package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.model.LanguageBoundEntity;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.repository.LanguageBoundRepository;

import java.util.List;

public interface LanguageBoundService<T extends LanguageBoundEntity, R extends LanguageBoundRepository<T>>
        extends ExtendableService<T, R> {

    /**
     * @return the objects of the current user that belong to the language profile
     */
    List<T> findAllForUser(Language language) throws Exception;

    List<Long> findAllIdsForUser(Language language) throws Exception;

    /**
     * Soft deletes everything of this kind that belongs to the language profile (used when the profile is deleted).
     */
    int softDeleteForLanguage(Language language) throws Exception;

}
