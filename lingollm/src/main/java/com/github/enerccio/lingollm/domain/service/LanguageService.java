package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.repository.LanguageRepository;

/**
 * The language profiles of the current user.
 */
public interface LanguageService extends ExtendableService<Language, LanguageRepository> {

    /**
     * The template of the prompt that makes the model write questions of a test (any test, the questions asked for are
     * a variable of it): the one the profile has if it is set, the default of its language ({@code Languages.defaults})
     * otherwise.
     */
    String getCreateTestPrompt(Language language);

    /**
     * The template of the prompt of the second pass over the texts of the questions the model wrote (Japanese: the
     * furigana), same rules as {@link #getCreateTestPrompt}. Blank for a language whose texts need no second pass.
     */
    String getAnnotateTextsPrompt(Language language);

}
