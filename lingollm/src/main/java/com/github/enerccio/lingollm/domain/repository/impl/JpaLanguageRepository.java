package com.github.enerccio.lingollm.domain.repository.impl;

import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.repository.LanguageRepository;

public class JpaLanguageRepository extends JpaExtendableRepository<Language> implements LanguageRepository {

    @Override
    protected Class<Language> getEntityClass() {
        return Language.class;
    }

}
