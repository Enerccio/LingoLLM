package com.github.enerccio.lingollm.domain.service.impl;

import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.repository.LanguageRepository;
import com.github.enerccio.lingollm.domain.service.FreeChatMessageService;
import com.github.enerccio.lingollm.domain.service.FreeChatService;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import com.github.enerccio.lingollm.domain.service.TestQuestionGroupService;
import com.github.enerccio.lingollm.domain.service.TestQuestionResultService;
import com.github.enerccio.lingollm.domain.service.TestQuestionService;
import com.github.enerccio.lingollm.domain.service.TestResultService;
import com.github.enerccio.lingollm.domain.service.TestService;
import com.github.enerccio.lingollm.domain.traits.CommonTx;
import com.github.enerccio.lingollm.ui.lang.LanguageDefaults;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.function.Function;

public class LanguageServiceImpl extends ExtendableServiceImpl<Language, LanguageRepository> implements LanguageService {

    @Autowired
    private FreeChatService chatService;

    @Autowired
    private FreeChatMessageService messageService;

    @Autowired
    private TestService testService;

    @Autowired
    private TestQuestionGroupService testQuestionGroupService;

    @Autowired
    private TestQuestionService testQuestionService;

    @Autowired
    private TestResultService testResultService;

    @Autowired
    private TestQuestionResultService testQuestionResultService;

    @Override
    public String getGaugeTestPrompt(Language language) {
        return resolve(language, LanguageDefaults::getGaugeTestPrompt);
    }

    /** What the profile set, the default of its language when it did not. */
    private static String resolve(Language language, Function<LanguageDefaults, String> text) {
        String own = language.getDefaults() == null ? null : text.apply(language.getDefaults());
        if (StringUtils.isNotBlank(own)) {
            return own;
        }
        return language.getLanguage() == null ? null : text.apply(language.getLanguage().defaults);
    }

    /**
     * A soft delete takes everything that belongs to the profile with it (one bulk update per kind, the rows are not
     * loaded).
     */
    @Override
    @CommonTx
    public Language delete(Language language, boolean hard) throws Exception {
        if (!hard && language != null && language.getId() != null) {
            messageService.softDeleteForLanguage(language);
            chatService.softDeleteForLanguage(language);
            testQuestionResultService.softDeleteForLanguage(language);
            testResultService.softDeleteForLanguage(language);
            testQuestionService.softDeleteForLanguage(language);
            testQuestionGroupService.softDeleteForLanguage(language);
            testService.softDeleteForLanguage(language);
        }
        return super.delete(language, hard);
    }

}
