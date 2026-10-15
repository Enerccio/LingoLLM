package com.github.enerccio.lingollm.domain.repository.impl;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionGroup;
import com.github.enerccio.lingollm.domain.repository.TestQuestionGroupRepository;

import java.util.Date;
import java.util.List;

public class JpaTestQuestionGroupRepository extends JpaLanguageBoundRepository<TestQuestionGroup>
        implements TestQuestionGroupRepository {

    @Override
    protected Class<TestQuestionGroup> getEntityClass() {
        return TestQuestionGroup.class;
    }

    @Override
    public List<TestQuestionGroup> findByTest(Test test) throws Exception {
        List<TestQuestionGroup> groups = entityManager.createQuery("SELECT e FROM TestQuestionGroup e WHERE e.deleted = false"
                        + " AND e.parentTest.id = :test ORDER BY e.ordinal, e.id", TestQuestionGroup.class)
                .setParameter("test", test.getId())
                .getResultList();
        for (TestQuestionGroup group : groups) {
            hydrate(group);
        }
        return groups;
    }

    @Override
    public int softDeleteByTest(Test test) {
        if (test == null || test.getId() == null) {
            return 0;
        }
        return entityManager.createQuery("UPDATE TestQuestionGroup e SET e.deleted = true, e.modification = :now"
                        + " WHERE e.parentTest.id = :test AND e.deleted = false")
                .setParameter("now", new Date())
                .setParameter("test", test.getId())
                .executeUpdate();
    }

    @Override
    public int hardDeleteByTest(Test test) {
        if (test == null || test.getId() == null) {
            return 0;
        }
        return entityManager.createQuery("DELETE FROM TestQuestionGroup e WHERE e.parentTest.id = :test")
                .setParameter("test", test.getId())
                .executeUpdate();
    }

}
