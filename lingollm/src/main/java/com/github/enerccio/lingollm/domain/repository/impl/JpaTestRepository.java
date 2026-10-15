package com.github.enerccio.lingollm.domain.repository.impl;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.repository.TestRepository;

public class JpaTestRepository extends JpaLanguageBoundRepository<Test> implements TestRepository {

    @Override
    protected Class<Test> getEntityClass() {
        return Test.class;
    }

}
