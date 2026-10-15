package com.github.enerccio.lingollm.ui.lang.languages.japanese;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * JLPT levels in the order of difficulty: {@link #N5} is the easiest and {@link #N1} the hardest, so the natural
 * order of the constants is easiest first.
 */
public enum JlptLevel {
    N5(5), N4(4), N3(3), N2(2), N1(1);

    private final int number;

    JlptLevel(int number) {
        this.number = number;
    }

    /** The number in the level name (5 for N5). */
    public int number() {
        return number;
    }

    /** The level for a number 1-5. */
    public static Optional<JlptLevel> fromNumber(int number) {
        return Arrays.stream(values()).filter(l -> l.number == number).findFirst();
    }

    /** True when this level is the same as or easier than {@code other}. */
    public boolean isEasierOrEqual(JlptLevel other) {
        return compareTo(other) <= 0;
    }

    /** All levels from {@code from} to {@code to} inclusive; the order of the arguments does not matter. */
    public static List<JlptLevel> range(JlptLevel from, JlptLevel to) {
        JlptLevel lo = from.compareTo(to) <= 0 ? from : to;
        JlptLevel hi = lo == from ? to : from;
        return Arrays.asList(values()).subList(lo.ordinal(), hi.ordinal() + 1);
    }
}
