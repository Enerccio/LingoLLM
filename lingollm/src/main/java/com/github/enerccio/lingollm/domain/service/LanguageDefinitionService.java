package com.github.enerccio.lingollm.domain.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;

import java.util.List;

/**
 * What the application knows about one learnable language ({@link Languages}). There is one implementation per
 * language, the UI of the language uses its own.
 */
public interface LanguageDefinitionService {

    /**
     * @return the language the service defines
     */
    Languages getLanguage();

    /**
     * The JSON schema of a {@link LanguageString} the model writes in this language (a question, an answer): the
     * language can ask for more than a text, e.g. Japanese needs furigana over every kanji. Complete, no references.
     */
    ObjectNode getLanguageStringSchema();

    /**
     * Tells the model how to write a {@link LanguageString} in this language, for its prompt (the schema says what
     * is allowed, this says what is wanted). The text is in English.
     */
    String getLanguageStringInstructions();

    /**
     * Checks a text the model wrote against what the language asks of it (the rules the schema can't express, like
     * "every kanji has furigana").
     *
     * @return what is wrong with it, empty when it is fine
     */
    List<String> checkLanguageString(LanguageString string);

    /**
     * Has the model write a test that gauges the level of the learner of the language, and saves it. The call returns
     * at once, the callback gets the test (or the failure) later, on another thread. Nothing is saved unless the whole
     * test is valid.
     *
     * @param language the profile the test is for, its language has to be {@link #getLanguage()}
     * @param ai       the inference provider that writes the test
     * @return cancels the request
     */
    CancellationToken createGaugeTest(Language language, AI ai, GaugeTestCallback callback) throws Exception;

    interface GaugeTestCallback {

        /** The test is written and saved, with all its questions in one group. */
        void onTest(Test test) throws Exception;

        /** The test could not be made: the model failed, answered something else than asked, or saving failed. */
        void onError(Throwable error) throws Exception;

        /** The request was cancelled. */
        default void onCancel() throws Exception {
        }
    }

}
