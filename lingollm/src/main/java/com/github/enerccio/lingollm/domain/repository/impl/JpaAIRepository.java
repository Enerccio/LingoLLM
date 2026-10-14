package com.github.enerccio.lingollm.domain.repository.impl;

import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.repository.AIRepository;
import com.github.enerccio.lingollm.domain.security.model.User;

import java.util.List;

/**
 * The AIs of a user are their own and the shared ones (see {@link AI#isShared()}).
 */
public class JpaAIRepository extends JpaExtendableRepository<AI> implements AIRepository {

    private static final String VISIBLE = "e.deleted = false AND (e.owner.id = :owner OR e.shared = true)";

    @Override
    protected Class<AI> getEntityClass() {
        return AI.class;
    }

    @Override
    public List<AI> findAll(User user) throws Exception {
        List<AI> entities = entityManager.createQuery("SELECT e FROM " + getEntityType() + " e WHERE " + VISIBLE, AI.class)
                .setParameter("owner", user.getId())
                .getResultList();
        for (AI ai : entities)
            hydrate(ai);
        return entities;
    }

    @Override
    public AI findByUuid(String uuid, User user) throws Exception {
        List<AI> results = entityManager.createQuery("SELECT e FROM " + getEntityType() + " e WHERE e.uuid = :uuid AND " + VISIBLE, AI.class)
                .setParameter("uuid", uuid)
                .setParameter("owner", user.getId())
                .setMaxResults(1)
                .getResultList();
        return results.isEmpty() ? null : hydrate(results.getFirst());
    }

    @Override
    public List<Long> findAllIds(User user) throws Exception {
        return entityManager.createQuery("SELECT e.id FROM " + getEntityType() + " e WHERE " + VISIBLE, Long.class)
                .setParameter("owner", user.getId())
                .getResultList();
    }
}
