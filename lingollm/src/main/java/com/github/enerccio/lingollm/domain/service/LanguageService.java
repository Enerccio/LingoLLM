package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.repository.LanguageRepository;

/**
 * The language profiles of the current user.
 */
public interface LanguageService extends ExtendableService<Language, LanguageRepository> {

    /**
     * The template of the prompt that makes the model write a level gauging test: the one the profile has if it is set,
     * the default of its language ({@code Languages.defaults}) otherwise.
     */
    String getGaugeTestPrompt(Language language);

}
