package com.github.enerccio.lingollm.ui.lang.languages.common.impl;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.TestQuestionHandler;
import org.springframework.beans.factory.annotation.Configurable;

import java.util.ArrayList;
import java.util.List;

/**
 * Put the segments of a sentence in order. The question keeps them in the <b>correct</b> order, whoever shows the
 * question shuffles them (and compares the learner's order with the stored one).
 */
@Configurable
public class WordOrderQuestionHandler extends TestQuestionHandler<WordOrderQuestionData> {

    public WordOrderQuestionHandler(Test test, TestQuestion testQuestion) {
        super(test, testQuestion);
    }

    @Override
    protected WordOrderQuestionData createEmpty() {
        return new WordOrderQuestionData();
    }

    @Override
    protected Class<WordOrderQuestionData> getType() {
        return WordOrderQuestionData.class;
    }

    @Override
    protected void check(WordOrderQuestionData data) {
        if (data.getSegments() == null || data.getSegments().size() < 2) {
            throw new IllegalArgumentException("A word order question needs at least two segments");
        }
        if (data.getSegments().stream().anyMatch(s -> s == null || s.getRawString().isBlank())) {
            throw new IllegalArgumentException("A segment can't be empty");
        }
    }

    /**
     * @param segments the segments of the sentence in the correct order
     */
    public void setSegments(List<LanguageString> segments) {
        WordOrderQuestionData data = getTestData(getType());
        data.setSegments(segments == null ? null : new ArrayList<>(segments));
        check(data);
        setTestData(data);
    }
}
