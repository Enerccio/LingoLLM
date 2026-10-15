package com.github.enerccio.lingollm.domain.repository.impl;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.domain.repository.TestQuestionRepository;

import java.util.Date;
import java.util.List;

public class JpaTestQuestionRepository extends JpaLanguageBoundRepository<TestQuestion> implements TestQuestionRepository {

    @Override
    protected Class<TestQuestion> getEntityClass() {
        return TestQuestion.class;
    }

    @Override
    public List<TestQuestion> findByTest(Test test) throws Exception {
        List<TestQuestion> questions = entityManager.createQuery("SELECT e FROM TestQuestion e LEFT JOIN e.questionGroup g WHERE e.deleted = false"
                        + " AND e.parentTest.id = :test ORDER BY g.ordinal, e.ordinal, e.id", TestQuestion.class)
                .setParameter("test", test.getId())
                .getResultList();
        for (TestQuestion question : questions) {
            hydrate(question);
        }
        return questions;
    }

    @Override
    public int softDeleteByTest(Test test) {
        if (test == null || test.getId() == null) {
            return 0;
        }
        return entityManager.createQuery("UPDATE TestQuestion e SET e.deleted = true, e.modification = :now"
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
        return entityManager.createQuery("DELETE FROM TestQuestion e WHERE e.parentTest.id = :test")
                .setParameter("test", test.getId())
                .executeUpdate();
    }

}
