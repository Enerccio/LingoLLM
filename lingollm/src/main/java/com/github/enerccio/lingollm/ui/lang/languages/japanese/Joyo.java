package com.github.enerccio.lingollm.ui.lang.languages.japanese;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The Jōyō kanji list, loaded once from {@code japanese/joyo.csv} on the classpath.
 * <p>
 * Conventions: grades are {@link SchoolGrade}s;
 * frequency is a rank (1 = most frequent, higher = rarer); JLPT levels are {@link JlptLevel}. A value
 * of 0 (no JLPT level: {@code null}) means "unknown" and such kanji are always sorted last and never match a range.
 */
public final class Joyo {

    private static final String RESOURCE = "/japanese/joyo.csv";

    private static final class Holder {
        static final Joyo INSTANCE = new Joyo();
    }

    public static Joyo getInstance() {
        return Holder.INSTANCE;
    }

    /** Unknown (0) values go last, otherwise ascending. */
    private static final Comparator<Integer> ASCENDING_UNKNOWN_LAST = (a, b) -> {
        if (a == 0 || b == 0) {
            return a == 0 ? (b == 0 ? 0 : 1) : -1;
        }
        return Integer.compare(a, b);
    };

    public static final Comparator<KanjiDef> BY_INDEX = Comparator.comparingInt(KanjiDef::index);
    /** Elementary grades first, then secondary school. */
    public static final Comparator<KanjiDef> BY_GRADE =
            Comparator.comparing(KanjiDef::grade, Comparator.nullsLast(Comparator.<SchoolGrade>naturalOrder())).thenComparing(BY_INDEX);
    /** Most frequent first. */
    public static final Comparator<KanjiDef> BY_FREQUENCY =
            Comparator.comparing(KanjiDef::frequency, ASCENDING_UNKNOWN_LAST).thenComparing(BY_INDEX);
    /** Easiest level (N5) first, N1 last. */
    public static final Comparator<KanjiDef> BY_JLPT =
            Comparator.comparing(KanjiDef::jlpt, Comparator.nullsLast(Comparator.<JlptLevel>naturalOrder())).thenComparing(BY_INDEX);
    public static final Comparator<KanjiDef> BY_STROKES =
            Comparator.comparingInt(KanjiDef::strokes).thenComparing(BY_INDEX);

    private final List<KanjiDef> all;
    private final Map<String, KanjiDef> byKanji = new HashMap<>();

    private Joyo() {
        List<KanjiDef> defs = new ArrayList<>();
        try (InputStream in = Joyo.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + RESOURCE);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                defs.add(KanjiDef.fromRow(parseCsvLine(line)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.all = List.copyOf(defs);
        for (KanjiDef def : all) {
            byKanji.put(def.kanji(), def);
        }
    }

    // --- lookup ---

    /** All kanji in the order of the list. */
    public List<KanjiDef> all() {
        return all;
    }

    public int size() {
        return all.size();
    }

    public Optional<KanjiDef> find(String kanji) {
        return kanji == null ? Optional.empty() : Optional.ofNullable(byKanji.get(kanji));
    }

    public Optional<KanjiDef> find(int codePoint) {
        return Optional.ofNullable(byKanji.get(new String(Character.toChars(codePoint))));
    }

    public boolean isJoyo(String kanji) {
        return kanji != null && byKanji.containsKey(kanji);
    }

    /** Definitions of the given kanji in the given order; unknown ones are skipped, duplicates kept. */
    public List<KanjiDef> find(Collection<String> kanji) {
        List<KanjiDef> result = new ArrayList<>();
        for (String k : kanji) {
            KanjiDef def = k == null ? null : byKanji.get(k);
            if (def != null) {
                result.add(def);
            }
        }
        return result;
    }

    /** Distinct Jōyō kanji appearing in a text, in order of first appearance. */
    public List<KanjiDef> findInText(String text) {
        if (text == null) {
            return List.of();
        }
        Set<KanjiDef> seen = new LinkedHashSet<>();
        text.codePoints().forEach(cp -> find(cp).ifPresent(seen::add));
        return new ArrayList<>(seen);
    }

    // --- grades ---

    /** Kanji of one grade. */
    public List<KanjiDef> byGrade(SchoolGrade grade) {
        return byGradeRange(grade, grade);
    }

    /** Kanji whose grade is within the inclusive range; the order of the arguments does not matter. */
    public List<KanjiDef> byGradeRange(SchoolGrade from, SchoolGrade to) {
        List<SchoolGrade> grades = SchoolGrade.range(from, to);
        return all.stream().filter(d -> grades.contains(d.grade())).toList();
    }

    public List<KanjiDef> elementary() {
        return byGradeRange(SchoolGrade.GRADE_1, SchoolGrade.GRADE_6);
    }

    public List<KanjiDef> secondary() {
        return byGrade(SchoolGrade.SECONDARY);
    }

    // --- frequency ---

    /** Kanji whose frequency rank is within the inclusive range (1 = most frequent), most frequent first. */
    public List<KanjiDef> byFrequencyRange(int fromRank, int toRank) {
        return sortByFrequency(inRange(KanjiDef::frequency, fromRank, toRank));
    }

    /** The {@code count} most frequent kanji. */
    public List<KanjiDef> mostFrequent(int count) {
        return all.stream().filter(KanjiDef::hasFrequency).sorted(BY_FREQUENCY).limit(Math.max(0, count)).toList();
    }

    /** The {@code count} least frequent kanji that have a known frequency, rarest first. */
    public List<KanjiDef> leastFrequent(int count) {
        return all.stream().filter(KanjiDef::hasFrequency).sorted(BY_FREQUENCY.reversed()).limit(Math.max(0, count))
                .toList();
    }

    // --- JLPT ---

    /** Kanji of one JLPT level. */
    public List<KanjiDef> byJlpt(JlptLevel level) {
        return byJlptRange(level, level);
    }

    /** Kanji whose JLPT level is within the inclusive range, e.g. N5..N4; argument order is free. */
    public List<KanjiDef> byJlptRange(JlptLevel from, JlptLevel to) {
        List<JlptLevel> levels = JlptLevel.range(from, to);
        return sortByJlpt(all.stream().filter(d -> levels.contains(d.jlpt())).toList());
    }

    /** Kanji of this level or an easier one (N5 for N5, N5+N4 for N4 ...), easiest first. */
    public List<KanjiDef> byJlptUpTo(JlptLevel level) {
        return byJlptRange(JlptLevel.N5, level);
    }

    // --- other filters ---

    public List<KanjiDef> byStrokes(int from, int to) {
        return inRange(KanjiDef::strokes, from, to);
    }

    public List<KanjiDef> byRadical(String radical) {
        return all.stream().filter(d -> d.radical().equals(radical)).toList();
    }

    /** Kanji with the given on'yomi or kun'yomi reading (okurigana ignored). */
    public List<KanjiDef> byReading(String reading) {
        return all.stream()
                .filter(d -> d.onReadings().contains(reading) || d.kunReadingsPlain().contains(reading)
                        || d.kunStems().contains(reading))
                .toList();
    }

    /** Kanji with a meaning containing the text, ignoring case. */
    public List<KanjiDef> byMeaning(String text) {
        String needle = text.toLowerCase();
        return all.stream()
                .filter(d -> d.meanings().stream().anyMatch(m -> m.toLowerCase().contains(needle)))
                .toList();
    }

    // --- sorting (all return a new list, the argument is untouched) ---

    public List<KanjiDef> sortByGrade(Collection<KanjiDef> defs) {
        return sorted(defs, BY_GRADE);
    }

    public List<KanjiDef> sortByFrequency(Collection<KanjiDef> defs) {
        return sorted(defs, BY_FREQUENCY);
    }

    public List<KanjiDef> sortByJlpt(Collection<KanjiDef> defs) {
        return sorted(defs, BY_JLPT);
    }

    public List<KanjiDef> sortByStrokes(Collection<KanjiDef> defs) {
        return sorted(defs, BY_STROKES);
    }

    public List<KanjiDef> sorted(Collection<KanjiDef> defs, Comparator<KanjiDef> comparator) {
        List<KanjiDef> result = new ArrayList<>(defs);
        result.sort(comparator);
        return result;
    }

    // --- grouping ---

    /** Groups by grade, in order of teaching; kanji without grade are skipped. */
    public Map<SchoolGrade, List<KanjiDef>> groupByGrade(Collection<KanjiDef> defs) {
        Map<SchoolGrade, List<KanjiDef>> map = new EnumMap<>(SchoolGrade.class);
        for (KanjiDef def : defs) {
            if (def.grade() != null) {
                map.computeIfAbsent(def.grade(), x -> new ArrayList<>()).add(def);
            }
        }
        return map;
    }

    /** Groups by JLPT level, easiest (N5) first; kanji without level are skipped. */
    public Map<JlptLevel, List<KanjiDef>> groupByJlpt(Collection<KanjiDef> defs) {
        Map<JlptLevel, List<KanjiDef>> map = new EnumMap<>(JlptLevel.class);
        for (KanjiDef def : defs) {
            if (def.hasJlpt()) {
                map.computeIfAbsent(def.jlpt(), x -> new ArrayList<>()).add(def);
            }
        }
        return map;
    }

    // --- helpers ---

    private List<KanjiDef> inRange(java.util.function.ToIntFunction<KanjiDef> value, int a, int b) {
        int lo = Math.max(1, Math.min(a, b));
        int hi = Math.max(a, b);
        return all.stream().filter(d -> {
            int v = value.applyAsInt(d);
            return v >= lo && v <= hi;
        }).toList();
    }

    /** Minimal CSV line parser: commas separate, double quotes protect commas, {@code ""} is a quote. */
    static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString());
        return fields;
    }
}
