package com.github.enerccio.lingollm.domain.repository.impl;

import com.github.enerccio.lingollm.domain.model.LanguageBoundEntity;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.repository.LanguageBoundRepository;
import com.github.enerccio.lingollm.domain.security.model.User;

import java.util.Date;
import java.util.List;

public abstract class JpaLanguageBoundRepository<T extends LanguageBoundEntity> extends JpaExtendableRepository<T>
        implements LanguageBoundRepository<T> {

    @Override
    public List<T> findAll(User user, Language language) throws Exception {
        List<T> entities = entityManager.createQuery("SELECT e FROM " + getEntityType()
                        + " e WHERE e.deleted = false AND e.owner.id = :owner AND e.language.id = :language ORDER BY e.id", getEntityClass())
                .setParameter("owner", user.getId())
                .setParameter("language", language.getId())
                .getResultList();
        for (T entity : entities) {
            hydrate(entity);
        }
        return entities;
    }

    @Override
    public int softDeleteByLanguage(Language language) {
        if (language == null || language.getId() == null) {
            return 0;
        }
        return entityManager.createQuery("UPDATE " + getEntityType() + " e SET e.deleted = true, e.modification = :now"
                        + " WHERE e.language.id = :language AND e.deleted = false")
                .setParameter("now", new Date())
                .setParameter("language", language.getId())
                .executeUpdate();
    }

    @Override
    public List<Long> findAllIds(User user, Language language) throws Exception {
        return entityManager.createQuery("SELECT e.id FROM " + getEntityType()
                        + " e WHERE e.deleted = false AND e.owner.id = :owner AND e.language.id = :language ORDER BY e.id", Long.class)
                .setParameter("owner", user.getId())
                .setParameter("language", language.getId())
                .getResultList();
    }

}
