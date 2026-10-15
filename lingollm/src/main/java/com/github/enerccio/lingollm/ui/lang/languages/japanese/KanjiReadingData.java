package com.github.enerccio.lingollm.ui.lang.languages.japanese;

import com.github.enerccio.lingollm.domain.traits.LLMField;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.impl.SingleChoiceData;

public class KanjiReadingData extends SingleChoiceData {

    @LLMField(description = "A sentence that contains the target kanji, a natural one")
    private LanguageString sentence;
    @LLMField(description = "The kanji, or the word of kanji, whose reading is asked: written exactly as in the sentence. "
            + "The choices are its possible readings in hiragana")
    private String targetKanji;

    public LanguageString getSentence() {
        return sentence;
    }

    public void setSentence(LanguageString sentence) {
        this.sentence = sentence;
    }

    public String getTargetKanji() {
        return targetKanji;
    }

    public void setTargetKanji(String targetKanji) {
        this.targetKanji = targetKanji;
    }
}
