package com.github.enerccio.lingollm.domain.repository.impl;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionResult;
import com.github.enerccio.lingollm.domain.model.impl.TestResult;
import com.github.enerccio.lingollm.domain.repository.TestQuestionResultRepository;

import java.util.Date;
import java.util.List;

public class JpaTestQuestionResultRepository extends JpaLanguageBoundRepository<TestQuestionResult>
        implements TestQuestionResultRepository {

    private static final String OF_TEST = " e.testResult.id IN (SELECT r.id FROM TestResult r WHERE r.test.id = :id)";

    @Override
    protected Class<TestQuestionResult> getEntityClass() {
        return TestQuestionResult.class;
    }

    @Override
    public List<TestQuestionResult> findByTestResult(TestResult result) throws Exception {
        List<TestQuestionResult> results = entityManager.createQuery("SELECT e FROM TestQuestionResult e WHERE e.deleted = false"
                        + " AND e.testResult.id = :id ORDER BY e.creation, e.id", TestQuestionResult.class)
                .setParameter("id", result.getId())
                .getResultList();
        for (TestQuestionResult r : results) {
            hydrate(r);
        }
        return results;
    }

    @Override
    public int softDeleteByTestResult(TestResult result) {
        return softDelete("e.testResult.id = :id", result == null ? null : result.getId());
    }

    @Override
    public int hardDeleteByTestResult(TestResult result) {
        return hardDelete("e.testResult.id = :id", result == null ? null : result.getId());
    }

    @Override
    public int softDeleteByTest(Test test) {
        return softDelete(OF_TEST, test == null ? null : test.getId());
    }

    @Override
    public int hardDeleteByTest(Test test) {
        return hardDelete(OF_TEST, test == null ? null : test.getId());
    }

    private int softDelete(String condition, Long id) {
        if (id == null) {
            return 0;
        }
        return entityManager.createQuery("UPDATE TestQuestionResult e SET e.deleted = true, e.modification = :now"
                        + " WHERE " + condition + " AND e.deleted = false")
                .setParameter("now", new Date())
                .setParameter("id", id)
                .executeUpdate();
    }

    private int hardDelete(String condition, Long id) {
        if (id == null) {
            return 0;
        }
        return entityManager.createQuery("DELETE FROM TestQuestionResult e WHERE " + condition)
                .setParameter("id", id)
                .executeUpdate();
    }

}
