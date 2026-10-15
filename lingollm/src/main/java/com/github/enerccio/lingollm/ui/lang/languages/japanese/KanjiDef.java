package com.github.enerccio.lingollm.ui.lang.languages.japanese;

import java.util.List;

/**
 * One row of {@code japanese/joyo.csv}: a Jōyō kanji with its readings, meanings and statistics.
 *
 * @param index       position in the Jōyō list
 * @param kanji       the character
 * @param oldForm     traditional (kyūjitai) form, empty when there is none
 * @param radical     radical
 * @param strokes     stroke count
 * @param grade       school grade in which the kanji is taught, {@code null} when not given
 * @param year        year the kanji was added to the list, 0 when it was in the original list
 * @param meanings    English meanings
 * @param onReadings  on'yomi readings (katakana)
 * @param kunReadings kun'yomi readings (hiragana, {@code -} separates the okurigana)
 * @param frequency   frequency rank in newspapers (1 = most frequent), 0 when unknown
 * @param jlpt        JLPT level, {@code null} when not given
 */
public record KanjiDef(int index, String kanji, String oldForm, String radical, int strokes, SchoolGrade grade, int year,
                       List<String> meanings, List<String> onReadings, List<String> kunReadings, int frequency,
                       JlptLevel jlpt) {

    /** The CSV separates several values in one column with this character. */
    private static final String LIST_SEPARATOR = "|";

    public KanjiDef {
        meanings = List.copyOf(meanings);
        onReadings = List.copyOf(onReadings);
        kunReadings = List.copyOf(kunReadings);
    }

    /** Builds a definition from one CSV row (columns in the order of the file header). */
    static KanjiDef fromRow(List<String> row) {
        return new KanjiDef(
                toInt(row.get(0)),
                row.get(1),
                row.get(2),
                row.get(3),
                toInt(row.get(4)),
                SchoolGrade.fromCode(row.get(5)).orElse(null),
                toInt(row.get(6)),
                toList(row.get(7)),
                toList(row.get(8)),
                toList(row.get(9)),
                toInt(row.get(10)),
                toJlpt(row.get(11)));
    }

    private static int toInt(String value) {
        return value == null || value.isBlank() ? 0 : Integer.parseInt(value.trim());
    }

    private static JlptLevel toJlpt(String value) {
        return value == null || value.isBlank() ? null : JlptLevel.fromNumber(toInt(value)).orElse(null);
    }

    private static List<String> toList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split("\\" + LIST_SEPARATOR));
    }

    public boolean hasOldForm() {
        return !oldForm.isEmpty();
    }

    public boolean isSecondarySchool() {
        return grade == SchoolGrade.SECONDARY;
    }

    /** True when the kanji is taught in elementary school (grades 1-6). */
    public boolean isElementarySchool() {
        return grade != null && grade.isElementary();
    }

    public boolean hasFrequency() {
        return frequency > 0;
    }

    public boolean hasJlpt() {
        return jlpt != null;
    }

    /** Kun'yomi readings without the okurigana separator, e.g. {@code あわ-れ} becomes {@code あわれ}. */
    public List<String> kunReadingsPlain() {
        return kunReadings.stream().map(r -> r.replace("-", "")).toList();
    }

    /** Kun'yomi stems only (the part before {@code -}), e.g. {@code あわ-れ} becomes {@code あわ}. */
    public List<String> kunStems() {
        return kunReadings.stream().map(r -> {
            int dash = r.indexOf('-');
            return dash < 0 ? r : r.substring(0, dash);
        }).distinct().toList();
    }

    @Override
    public String toString() {
        return kanji;
    }
}
