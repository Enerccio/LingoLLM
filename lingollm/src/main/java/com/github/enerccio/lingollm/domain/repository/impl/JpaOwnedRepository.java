package com.github.enerccio.lingollm.domain.repository.impl;

import com.github.enerccio.lingollm.domain.model.OwnedEntity;
import com.github.enerccio.lingollm.domain.repository.OwnedRepository;
import com.github.enerccio.lingollm.domain.security.model.User;
import jakarta.persistence.TypedQuery;

import java.util.List;

public abstract class JpaOwnedRepository<T extends OwnedEntity> extends JpaBaseRepository<T> implements OwnedRepository<T> {

    @Override
    public List<T> findAll(User user) throws Exception {
        TypedQuery<T> query = entityManager.createQuery("SELECT e FROM " + getEntityType() + " e WHERE e.deleted = false AND e.owner.id = :owner", getEntityClass())
                .setParameter("owner", user.getId());
        List<T> entities = query.getResultList();
        for (T t : entities)
            hydrate(t);
        return entities;
    }

    @Override
    public T findByUuid(String uuid, User user) throws Exception {
        List<T> results = entityManager.createQuery("SELECT e FROM " + getEntityType() + " e WHERE e.uuid = :uuid AND e.deleted = false AND e.owner.id = :owner", getEntityClass())
                .setParameter("uuid", uuid)
                .setParameter("owner", user.getId())
                .setMaxResults(1)
                .getResultList();
        return results.isEmpty() ? null : hydrate(results.getFirst());
    }

    @Override
    public List<Long> findAllIds(User user) throws Exception {
        TypedQuery<Long> query = entityManager.createQuery("SELECT e.id FROM " + getEntityType() + " e WHERE e.deleted = false AND e.owner.id = :owner", Long.class)
                .setParameter("owner", user.getId());
        return query.getResultList();
    }

}
