package com.github.enerccio.lingollm.domain.repository.impl;

import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.model.impl.Usage;
import com.github.enerccio.lingollm.domain.repository.UsageRepository;
import com.github.enerccio.lingollm.domain.security.model.User;

import java.util.Date;
import java.util.List;

public class JpaUsageRepository extends JpaOwnedRepository<Usage> implements UsageRepository {

    @Override
    protected Class<Usage> getEntityClass() {
        return Usage.class;
    }

    @Override
    public Usage findByAI(AI ai, User user) {
        List<Usage> results = getEntityManager()
                .createQuery("SELECT u FROM Usage u WHERE u.ai.id = :ai AND u.owner.id = :owner AND u.deleted = false", Usage.class)
                .setParameter("ai", ai.getId())
                .setParameter("owner", user.getId())
                .setMaxResults(1)
                .getResultList();
        return results.isEmpty() ? null : results.getFirst();
    }

    @Override
    public List<Usage> findAllByAI(AI ai) {
        return getEntityManager()
                .createQuery("SELECT u FROM Usage u WHERE u.ai.id = :ai AND u.deleted = false", Usage.class)
                .setParameter("ai", ai.getId())
                .getResultList();
    }

    @Override
    public List<Usage> findAllUsages() {
        return getEntityManager()
                .createQuery("SELECT u FROM Usage u WHERE u.deleted = false", Usage.class)
                .getResultList();
    }

    @Override
    public int add(AI ai, User user, long prefillTokens, long generatedTokens, boolean limited) {
        Date now = new Date();
        String period = limited
                ? "u.periodPrefillTokens = u.periodPrefillTokens + :prefill, u.periodGeneratedTokens = u.periodGeneratedTokens + :generated, "
                : "u.periodPrefillTokens = 0, u.periodGeneratedTokens = 0, ";
        return getEntityManager()
                .createQuery("UPDATE Usage u SET u.prefillTokens = u.prefillTokens + :prefill, u.generatedTokens = u.generatedTokens + :generated, "
                        + period + "u.requests = u.requests + 1, u.lastUsed = :now, u.modification = :now "
                        + "WHERE u.ai.id = :ai AND u.owner.id = :owner AND u.deleted = false")
                .setParameter("prefill", prefillTokens)
                .setParameter("generated", generatedTokens)
                .setParameter("now", now)
                .setParameter("ai", ai.getId())
                .setParameter("owner", user.getId())
                .executeUpdate();
    }

    @Override
    public int resetPeriod(AI ai, User user, Date periodStart) {
        return getEntityManager()
                .createQuery("""
                        UPDATE Usage u SET u.periodPrefillTokens = 0, u.periodGeneratedTokens = 0 \
                        WHERE u.ai.id = :ai AND u.owner.id = :owner AND u.deleted = false AND u.lastUsed < :start""")
                .setParameter("start", periodStart)
                .setParameter("ai", ai.getId())
                .setParameter("owner", user.getId())
                .executeUpdate();
    }
}
