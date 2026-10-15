package com.github.enerccio.lingollm.domain.repository.impl;

import com.github.enerccio.lingollm.domain.collections.TestType;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestResult;
import com.github.enerccio.lingollm.domain.repository.TestResultRepository;

import com.github.enerccio.lingollm.domain.security.model.User;

import java.util.Date;
import java.util.List;

public class JpaTestResultRepository extends JpaLanguageBoundRepository<TestResult> implements TestResultRepository {

    @Override
    protected Class<TestResult> getEntityClass() {
        return TestResult.class;
    }

    @Override
    public List<TestResult> findByTest(Test test) throws Exception {
        List<TestResult> results = entityManager.createQuery("SELECT e FROM TestResult e WHERE e.deleted = false"
                        + " AND e.test.id = :test ORDER BY e.creation, e.id", TestResult.class)
                .setParameter("test", test.getId())
                .getResultList();
        for (TestResult result : results) {
            hydrate(result);
        }
        return results;
    }

    @Override
    public List<TestResult> findUnfinished(User user, Language language, TestType type) throws Exception {
        List<TestResult> results = entityManager.createQuery("SELECT e FROM TestResult e WHERE e.deleted = false"
                        + " AND e.finished = false AND e.owner.id = :owner AND e.language.id = :language"
                        + " AND e.test.deleted = false AND e.test.type = :type ORDER BY e.creation DESC, e.id DESC", TestResult.class)
                .setParameter("owner", user.getId())
                .setParameter("language", language.getId())
                .setParameter("type", type)
                .getResultList();
        for (TestResult result : results) {
            hydrate(result);
        }
        return results;
    }

    @Override
    public int softDeleteByTest(Test test) {
        if (test == null || test.getId() == null) {
            return 0;
        }
        return entityManager.createQuery("UPDATE TestResult e SET e.deleted = true, e.modification = :now"
                        + " WHERE e.test.id = :test AND e.deleted = false")
                .setParameter("now", new Date())
                .setParameter("test", test.getId())
                .executeUpdate();
    }

    @Override
    public int hardDeleteByTest(Test test) {
        if (test == null || test.getId() == null) {
            return 0;
        }
        return entityManager.createQuery("DELETE FROM TestResult e WHERE e.test.id = :test")
                .setParameter("test", test.getId())
                .executeUpdate();
    }

}
