package com.github.enerccio.lingollm.ui.lang.languages.japanese;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The school grade in which a Jōyō kanji is taught: elementary grades 1-6 and secondary school. The natural order of
 * the constants is the order of teaching.
 */
public enum SchoolGrade {
    GRADE_1("1"), GRADE_2("2"), GRADE_3("3"), GRADE_4("4"), GRADE_5("5"), GRADE_6("6"), SECONDARY("S");

    private final String code;

    SchoolGrade(String code) {
        this.code = code;
    }

    /** The value used in {@code joyo.csv}: {@code 1}-{@code 6} or {@code S}. */
    public String code() {
        return code;
    }

    public boolean isElementary() {
        return this != SECONDARY;
    }

    /** The grade for a CSV value ({@code 1}-{@code 6} or {@code S}, case insensitive). */
    public static Optional<SchoolGrade> fromCode(String code) {
        return code == null ? Optional.empty()
                : Arrays.stream(values()).filter(g -> g.code.equalsIgnoreCase(code.trim())).findFirst();
    }

    /** The elementary grade for the number 1-6. */
    public static Optional<SchoolGrade> fromNumber(int number) {
        return number >= 1 && number <= 6 ? Optional.of(values()[number - 1]) : Optional.empty();
    }

    /** All grades from {@code from} to {@code to} inclusive; the order of the arguments does not matter. */
    public static List<SchoolGrade> range(SchoolGrade from, SchoolGrade to) {
        SchoolGrade lo = from.compareTo(to) <= 0 ? from : to;
        SchoolGrade hi = lo == from ? to : from;
        return Arrays.asList(values()).subList(lo.ordinal(), hi.ordinal() + 1);
    }
}
