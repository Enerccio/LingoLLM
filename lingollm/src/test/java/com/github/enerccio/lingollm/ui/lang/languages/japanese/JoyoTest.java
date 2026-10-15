package com.github.enerccio.lingollm.ui.lang.languages.japanese;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JoyoTest {

    private final Joyo joyo = Joyo.getInstance();

    @Test
    void loadsAllRows() {
        assertEquals(2136, joyo.size());
        assertSame(joyo, Joyo.getInstance());
    }

    @Test
    void findsKanji() {
        KanjiDef def = joyo.find("愛").orElseThrow();
        assertEquals(SchoolGrade.GRADE_4, def.grade());
        assertEquals(13, def.strokes());
        assertEquals(780, def.frequency());
        assertEquals(JlptLevel.N3, def.jlpt());
        assertEquals(List.of("アイ"), def.onReadings());
        assertTrue(joyo.find("x").isEmpty());
        assertEquals(SchoolGrade.SECONDARY, joyo.find("亜").orElseThrow().grade());
        assertEquals(List.of("あわ-れ", "あわ-れむ"), joyo.find("哀").orElseThrow().kunReadings());
    }

    @Test
    void findsInTextAndLists() {
        assertEquals(2, joyo.findInText("愛は愛、哀 abc").size());
        assertEquals(2, joyo.find(List.of("愛", "?", "哀")).size());
    }

    @Test
    void gradeFrequencyAndJlptRanges() {
        assertEquals(80, joyo.byGrade(SchoolGrade.GRADE_1).size());
        assertEquals(240, joyo.byGradeRange(SchoolGrade.GRADE_2, SchoolGrade.GRADE_1).size());
        List<KanjiDef> top = joyo.mostFrequent(10);
        assertEquals(10, top.size());
        assertEquals(top, joyo.sortByFrequency(top.reversed()));
        assertTrue(joyo.byFrequencyRange(1, 100).size() <= 100);
        List<KanjiDef> n5n4 = joyo.byJlptUpTo(JlptLevel.N4);
        assertFalse(n5n4.isEmpty());
        assertTrue(n5n4.stream().allMatch(d -> d.jlpt() != null && d.jlpt().isEasierOrEqual(JlptLevel.N4)));
        assertEquals(JlptLevel.N5, n5n4.getFirst().jlpt());
        assertEquals(joyo.byJlpt(JlptLevel.N3), joyo.byJlptRange(JlptLevel.N3, JlptLevel.N3));
    }

    @Test
    void jlptLevel() {
        assertEquals(JlptLevel.N5, JlptLevel.fromNumber(5).orElseThrow());
        assertTrue(JlptLevel.fromNumber(6).isEmpty());
        assertEquals(List.of(JlptLevel.N4, JlptLevel.N3), JlptLevel.range(JlptLevel.N3, JlptLevel.N4));
        assertTrue(JlptLevel.N5.isEasierOrEqual(JlptLevel.N1));
    }

    @Test
    void schoolGrade() {
        assertEquals(SchoolGrade.SECONDARY, SchoolGrade.fromCode("S").orElseThrow());
        assertEquals(SchoolGrade.GRADE_3, SchoolGrade.fromCode("3").orElseThrow());
        assertEquals(SchoolGrade.GRADE_6, SchoolGrade.fromNumber(6).orElseThrow());
        assertTrue(SchoolGrade.fromNumber(7).isEmpty());
        assertEquals(1110, joyo.secondary().size());
        assertEquals(1026, joyo.elementary().size());
    }

    @Test
    void sorting() {
        List<KanjiDef> mixed = joyo.find(List.of("亜", "愛", "一"));
        assertEquals("一", joyo.sortByGrade(mixed).getFirst().kanji());
        assertEquals("亜", joyo.sortByGrade(mixed).getLast().kanji());
        assertEquals("亜", joyo.sortByJlpt(mixed).getLast().kanji());
        assertEquals(3, joyo.groupByGrade(mixed).size());
    }
}
