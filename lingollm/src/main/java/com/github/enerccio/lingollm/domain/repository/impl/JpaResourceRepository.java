package com.github.enerccio.lingollm.domain.repository.impl;

import com.github.enerccio.lingollm.domain.model.BaseEntity;
import com.github.enerccio.lingollm.domain.model.impl.Resource;
import com.github.enerccio.lingollm.domain.repository.ResourceRepository;
import com.github.enerccio.lingollm.domain.security.model.User;

import jakarta.persistence.metamodel.EntityType;

import java.util.List;
import java.util.Optional;

public class JpaResourceRepository extends JpaOwnedRepository<Resource> implements ResourceRepository {

    @Override
    protected Class<Resource> getEntityClass() {
        return Resource.class;
    }

    @Override
    public Resource findByHash(String hash, User owner) throws Exception {
        List<Resource> resources = getEntityManager()
                .createQuery("SELECT r FROM Resource r WHERE r.hash = :hash AND r.owner.id = :owner", Resource.class)
                .setParameter("owner", owner.getId())
                .setParameter("hash", hash)
                .getResultList();
        return resources.isEmpty() ? null : resources.getFirst();
    }

    @Override
    public List<Resource> findPage(User owner, int offset, int limit, String sortProperty, boolean ascending) {
        // the property is put into the query, so only known ones are accepted
        String property = switch (String.valueOf(sortProperty)) {
            case "originalName", "mimeType", "size" -> sortProperty;
            default -> "creation";
        };
        return getEntityManager()
                .createQuery("SELECT r FROM Resource r WHERE r.deleted = false AND r.owner.id = :owner ORDER BY r."
                        + property + (ascending ? " ASC" : " DESC") + ", r.id " + (ascending ? "ASC" : "DESC"), Resource.class)
                .setParameter("owner", owner.getId())
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public long count(User owner) {
        return getEntityManager()
                .createQuery("SELECT COUNT(r) FROM Resource r WHERE r.deleted = false AND r.owner.id = :owner", Long.class)
                .setParameter("owner", owner.getId())
                .getSingleResult();
    }

    @Override
    public boolean entityExists(String clazz, Long id) {
        if (clazz == null || id == null) {
            return false;
        }
        // the class name is matched against the entities of the persistence unit, it is never loaded by name
        Optional<EntityType<?>> entity = getEntityManager().getMetamodel().getEntities().stream()
                .filter(e -> e.getJavaType().getName().equals(clazz) && BaseEntity.class.isAssignableFrom(e.getJavaType()))
                .findFirst();
        if (entity.isEmpty()) {
            return false;
        }
        return getEntityManager()
                .createQuery("SELECT COUNT(e) FROM " + entity.get().getName() + " e WHERE e.id = :id AND e.deleted = false", Long.class)
                .setParameter("id", id)
                .getSingleResult() > 0;
    }
}
