package com.github.enerccio.lingollm.loc;

import java.text.Collator;
import java.util.Comparator;

public class NaturalOrderComparator implements Comparator<String> {

    private final Collator collator;

    public NaturalOrderComparator(Collator collator) {
        this.collator = collator;
    }

    public int compare(String a, String b) {
        int ia = 0, ib = 0;
        int nza, nzb;
        char ca, cb;

        while (true) {
            // Only count the number of zeroes leading the last number compared
            nza = nzb = 0;

            ca = charAt(a, ia);
            cb = charAt(b, ib);

            // skip over leading spaces or zeros
            while (Character.isSpaceChar(ca) || ca == '0') {
                if (ca == '0') {
                    nza++;
                } else {
                    // Only count consecutive zeroes
                    nza = 0;
                }

                ca = charAt(a, ++ia);
            }

            while (Character.isSpaceChar(cb) || cb == '0') {
                if (cb == '0') {
                    nzb++;
                } else {
                    // Only count consecutive zeroes
                    nzb = 0;
                }

                cb = charAt(b, ++ib);
            }

            // Process run of digits
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int bias = compareRight(a.substring(ia), b.substring(ib));
                if (bias != 0) {
                    return bias;
                }
            }

            if (ca == 0 && cb == 0) {
                // The strings compare the same. Perhaps the caller
                // will want to call strcmp to break the tie.
                return compareEqual(a, b, nza, nzb);
            }
            if (ca < cb) {
                return -1;
            }
            if (ca > cb) {
                return 1;
            }

            ++ia;
            ++ib;
        }
    }

    private static boolean isDigit(char c) {
        return Character.isDigit(c) || c == '.' || c == ',';
    }

    private static char charAt(String s, int i) {
        return i >= s.length() ? 0 : s.charAt(i);
    }

    private int compareEqual(String a, String b, int nza, int nzb) {
        if (nza - nzb != 0)
            return nza - nzb;

        if (a.length() == b.length())
            return collator.compare(a, b);

        return a.length() - b.length();
    }

    private static int compareRight(String a, String b) {
        int bias = 0, ia = 0, ib = 0;

        for (; ; ia++, ib++) {
            char ca = charAt(a, ia);
            char cb = charAt(b, ib);

            if (!isDigit(ca) && !isDigit(cb)) {
                return bias;
            }
            if (!isDigit(ca)) {
                return -1;
            }
            if (!isDigit(cb)) {
                return 1;
            }
            if (ca == 0 && cb == 0) {
                return bias;
            }

            if (bias == 0) {
                if (ca < cb) {
                    bias = -1;
                } else if (ca > cb) {
                    bias = 1;
                }
            }
        }
    }
}