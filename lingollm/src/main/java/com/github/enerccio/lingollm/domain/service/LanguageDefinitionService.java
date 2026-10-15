package com.github.enerccio.lingollm.domain.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.enerccio.lingollm.domain.collections.Languages;
import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.service.dto.TestQuestionRequest;
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
     * Has the model write the test that gauges the level of the learner of the language, and saves it: the questions
     * of {@code BaseLanguageDefinitionService.gaugeQuestionRequests}, see {@link #createTest}.
     *
     * @param language the profile the test is for, its language has to be {@link #getLanguage()}
     * @param ai       the inference provider that writes the test
     * @return cancels the request
     */
    CancellationToken createGaugeTest(Language language, AI ai, TestCallback callback) throws Exception;

    /**
     * Has the model write a test of the questions asked for, and saves it. The model writes the questions a few at a
     * time (a batch is asked again when the model does not write what was asked), so a test takes as many requests as
     * it has batches; the questions are stitched into one test, all in one group, and nothing is saved unless all of
     * them are valid. The call returns at once (the first request is sent from the calling thread, so a limit of the
     * user or a missing prompt fails here), the callback gets the progress (the first one from the calling thread), the
     * test or the failure later, on another thread.
     *
     * @param language the profile the test is for, its language has to be {@link #getLanguage()}
     * @param ai       the inference provider that writes the test
     * @param name     the name of the test
     * @param requests the questions in the order they are asked: the kind and the level of every one
     * @return cancels the request
     */
    CancellationToken createTest(Language language, AI ai, String name, List<TestQuestionRequest> requests,
                                 TestCallback callback) throws Exception;

    interface TestCallback {

        /** The test is written and saved, with all its questions in one group. */
        void onTest(Test test) throws Exception;

        /** The test could not be made: the model failed, answered something else than asked, or saving failed. */
        void onError(Throwable error) throws Exception;

        /** The request was cancelled. */
        default void onCancel() throws Exception {
        }

        /**
         * {@code done} of the {@code total} questions of the test are written and accepted (a batch at a time); called
         * with 0 before the first request is sent.
         */
        default void onProgress(int done, int total) throws Exception {
        }
    }

}
