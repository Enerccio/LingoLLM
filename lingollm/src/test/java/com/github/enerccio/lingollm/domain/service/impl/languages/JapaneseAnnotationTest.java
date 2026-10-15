package com.github.enerccio.lingollm.domain.service.impl.languages;

import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageString;
import com.github.enerccio.lingollm.ui.lang.languages.common.LanguageStringMetadata;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/** The furigana pass of Japanese: the parts the model writes become a text with furigana ranges. */
class JapaneseAnnotationTest {

    private final JapaneseLanguageDefinitionServiceImpl japanese = new JapaneseLanguageDefinitionServiceImpl();

    private LanguageString read(String json, String expected, Set<String> whole, List<String> problems) {
        return japanese.readAnnotatedText(JsonParser.parseString(json), expected, whole, problems);
    }

    @Test
    void onlyTextsWithKanjiNeedThePass() {
        assertThat(japanese.needsAnnotation("日本語")).isTrue();
        assertThat(japanese.needsAnnotation("ねこがいる。 Hello")).isFalse();
    }

    @Test
    void findsTheWordsInTheTextAndTakesTheKanaAtTheEndsOutOfTheFurigana() {
        List<String> problems = new ArrayList<>();
        LanguageString sentence = read("""
                {"readings": [{"word": "お金", "reading": "おかね"}, {"word": "食べ", "reading": "たべ"}]}""",
                "お金を食べます。", Set.of(), problems);

        assertThat(problems).isEmpty();
        assertThat(sentence.getRawString()).isEqualTo("お金を食べます。");
        assertThat(sentence.getMetadata()).extracting(LanguageStringMetadata::getStart, LanguageStringMetadata::getEnd,
                LanguageStringMetadata::getValue).containsExactly(tuple(1, 2, "かね"), tuple(3, 4, "た"));
    }

    @Test
    void doesNotCareAboutTheRestOfTheText() {
        // the model does not write the text, so it can't drop the punctuation or the pipes of it
        List<String> problems = new ArrayList<>();
        LanguageString string = read("""
                {"readings": [{"word": "東京", "reading": "とうきょう"}, {"word": "大阪", "reading": "おおさか"},
                {"word": "京都", "reading": "きょうと"}, {"word": "札幌", "reading": "さっぽろ"}]}""",
                "東京|大阪|京都|札幌", Set.of(), problems);

        assertThat(problems).isEmpty();
        assertThat(string.getRawString()).isEqualTo("東京|大阪|京都|札幌");
        assertThat(string.getMetadata()).extracting(LanguageStringMetadata::getStart, LanguageStringMetadata::getEnd)
                .containsExactly(tuple(0, 2), tuple(3, 5), tuple(6, 8), tuple(9, 11));
    }

    @Test
    void aWordThatCameAgainWithoutAnEntryGetsTheSameReading() {
        List<String> problems = new ArrayList<>();
        LanguageString string = read("""
                {"readings": [{"word": "学校", "reading": "がっこう"}]}""", "学校へ行く。学校は広い。", Set.of(), problems);

        assertThat(problems.toString()).contains("行", "広");
        problems.clear();
        string = read("""
                {"readings": [{"word": "学校", "reading": "がっこう"}, {"word": "行", "reading": "い"}, {"word": "広", "reading": "ひろ"}]}""",
                "学校へ行く。学校は広い。", Set.of(), problems);
        assertThat(problems).isEmpty();
        assertThat(string.getMetadata()).extracting(LanguageStringMetadata::getStart, LanguageStringMetadata::getValue)
                .containsExactly(tuple(0, "がっこう"), tuple(3, "い"), tuple(6, "がっこう"), tuple(9, "ひろ"));
    }

    @Test
    void wantsAReadingOfEveryKanji() {
        List<String> problems = new ArrayList<>();
        assertThat(read("""
                {"readings": [{"word": "日本語", "reading": "にほんご"}]}""", "日本語を本で読む", Set.of(), problems)).isNull();
        assertThat(problems).hasSize(1).first().asString().contains("no reading for 本, 読");
    }

    @Test
    void ignoresWordsThatAreNotInTheTextButNamesThemWhenAKanjiHasNoReading() {
        List<String> problems = new ArrayList<>();
        // an extra word does not matter when everything is read
        assertThat(read("""
                {"readings": [{"word": "犬", "reading": "いぬ"}, {"word": "猫", "reading": "ねこ"}]}""", "猫です", Set.of(), problems))
                .isNotNull();
        assertThat(problems).isEmpty();
        // the word that was meant was written another way: the kanji is not read, the model is told
        assertThat(read("""
                {"readings": [{"word": "系統", "reading": "けいとう"}]}""", "既存の", Set.of(), problems)).isNull();
        assertThat(problems).hasSize(1).first().asString().contains("no reading for 既存").contains("系統 are not in the text");
    }

    @Test
    void refusesWhatIsNotAnEntry() {
        List<String> problems = new ArrayList<>();
        assertThat(read("{\"readings\": [{\"word\": \"猫\"}]}", "猫", Set.of(), problems)).isNull();
        assertThat(read("{\"nothing\": 1}", "猫", Set.of(), problems)).isNull();
        assertThat(problems).hasSize(2);
    }

    @Test
    void aTargetWordIsNotSplit() {
        String split = """
                {"readings": [{"word": "天", "reading": "てん"}, {"word": "気", "reading": "き"}]}""";
        String whole = """
                {"readings": [{"word": "天気", "reading": "てんき"}]}""";
        List<String> problems = new ArrayList<>();

        assertThat(read(split, "天気です", Set.of(), problems)).isNotNull();
        assertThat(read(split, "天気です", Set.of("天気"), problems)).isNull();
        assertThat(problems).hasSize(1).first().asString().contains("\"天気\" must be one word");
        problems.clear();
        assertThat(read(whole, "天気です", Set.of("天気"), problems)).isNotNull();
        // a word with okurigana: the furigana covers the kanji only
        assertThat(read("""
                {"readings": [{"word": "食べる", "reading": "たべる"}]}""", "食べる", Set.of("食べる"), problems)).isNotNull();
        assertThat(problems).isEmpty();
    }

    @Test
    void theTargetsAreTheTargetKanjiOfTheQuestions() {
        JsonArray questions = JsonParser.parseString("""
                [{"type": "KanjiReadingHandler", "targetKanji": "天気"}, {"type": "X"}, {"targetKanji": "ねこ"}]""").getAsJsonArray();
        JsonElement first = questions.get(0);

        assertThat(first).isNotNull();
        assertThat(japanese.wholeWords(questions)).containsExactly("天気");
    }
}
