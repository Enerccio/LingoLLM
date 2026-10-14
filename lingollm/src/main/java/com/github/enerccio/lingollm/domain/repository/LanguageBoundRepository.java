package com.github.enerccio.lingollm.domain.repository;

import com.github.enerccio.lingollm.domain.model.LanguageBoundEntity;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.security.model.User;

import java.util.List;

public interface LanguageBoundRepository<T extends LanguageBoundEntity> extends ExtendableRepository<T> {

    /**
     * @return the objects of the user that belong to the language profile
     */
    List<T> findAll(User user, Language language) throws Exception;

    List<Long> findAllIds(User user, Language language) throws Exception;

    /**
     * Soft deletes everything that belongs to the language profile with one bulk update: the rows are not loaded and
     * no entity listener runs.
     *
     * @return how many rows were deleted
     */
    int softDeleteByLanguage(Language language) throws Exception;

}
