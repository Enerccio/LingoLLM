package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.repository.LanguageRepository;

/**
 * The language profiles of the current user.
 */
public interface LanguageService extends ExtendableService<Language, LanguageRepository> {

}
