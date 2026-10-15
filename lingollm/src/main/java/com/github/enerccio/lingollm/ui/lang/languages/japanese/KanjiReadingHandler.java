package com.github.enerccio.lingollm.ui.lang.languages.japanese;

import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestion;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.ui.components.LanguageStringComponent;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.impl.SingleChoiceHandler;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Configurable;

/**
 * Pick the reading of a kanji (or a word of kanji) in a sentence. It is a single choice question whose choices are the
 * readings. The model writes the furigana of the whole sentence as for any text, the question hides the ones over the
 * {@link KanjiReadingData#getTargetKanji() target}, which would give the answer away.
 */
@Configurable
public class KanjiReadingHandler extends SingleChoiceHandler<KanjiReadingData> {

    @Override
    public String getLlmDescription() {
        return """
                The learner reads a kanji in a sentence. "sentence" is a natural sentence, "targetKanji" is the kanji (or the word
                of kanji) of the sentence whose reading is asked, written exactly as in the sentence. The application hides the
                furigana over the target. "choices" are readings of the target in hiragana (the right one and plausible wrong ones:
                another reading of the same kanji, a similar sounding word) and "correctAnswer" is the index of the reading the
                target has in this sentence. The "instructions" ask the learner to choose the reading of the target.""";
    }

    public KanjiReadingHandler(Test test, TestQuestion testQuestion) {
        super(test, testQuestion, KanjiReadingData.class, KanjiReadingData::new);
    }

    @Override
    protected void check(KanjiReadingData data) {
        super.check(data);
        if (data.getSentence() == null || data.getSentence().getRawString().isBlank()) {
            throw new IllegalArgumentException("A kanji reading question needs a sentence");
        }
        if (StringUtils.isBlank(data.getTargetKanji())) {
            throw new IllegalArgumentException("A kanji reading question needs the target kanji");
        }
        if (!data.getSentence().getRawString().contains(data.getTargetKanji())) {
            throw new IllegalArgumentException("The target kanji " + data.getTargetKanji() + " is not in the sentence");
        }
    }

    public void setSentence(LanguageString sentence, String targetKanji) {
        KanjiReadingData data = getTestData(getType());
        data.setSentence(sentence);
        data.setTargetKanji(targetKanji);
        setTestData(data);
    }

    /**
     * The sentence without the furigana of the target and which kanji is asked.
     */
    @Override
    protected Component renderPassage(KanjiReadingData data) {
        if (data.getSentence() == null) {
            return null;
        }
        LanguageStringComponent sentence = new LanguageStringComponent(data.getSentence());
        sentence.setAnnotationFilter(Furigana.hideOver(data.getTargetKanji()));

        Div content = new Div(sentence);
        if (StringUtils.isNotBlank(data.getTargetKanji())) {
            Span target = new Span(data.getTargetKanji());
            target.addClassName(CssStyles.KANJI_TARGET);
            Div question = new Div(new Span(loc.getValue(L.LABEL_READING_OF) + ": "), target);
            question.addClassName(CssStyles.TEXT_CENTER);
            content.add(question);
        }
        return passage(content);
    }
}
