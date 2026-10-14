package com.github.enerccio.lingollm.instruct.fixture;

import java.io.IOException;
import java.util.List;

/**
 * Bytecode shapes {@code ExtendableMethodVisitor} must handle. Deliberately NOT {@code @Extendable}: tests transform
 * its class file directly, so the runtime agent (if installed by another test) must not instrument it as well.
 * <p>
 * Every method is deterministic, so the original and the instrumented class can be compared call by call.
 */
@SuppressWarnings({"unused", "StringBufferReplaceableByString", "ConstantValue"})
public class InstrumentedSubject {

    public static int staticCalls;
    public int constructorCalls;
    public int finallyCount;

    public InstrumentedSubject() {
        constructorCalls++;
    }

    public void noop() {
    }

    public String echo(String text) {
        return text;
    }

    public int add(int a, int b) {
        return a + b;
    }

    /**
     * Two-slot arguments (long, double) before other arguments and locals - slot remapping must account for them.
     */
    public String wide(long big, int small, double ratio, String label) {
        String combined = label + ":" + big;
        long doubled = big * 2;
        double scaled = ratio * small;
        String result = combined + "/" + doubled + "/" + scaled;
        return result;
    }

    public double scale(double value, float factor) {
        return value * factor;
    }

    public String primitives(boolean flag, byte b, char c, short s, int i, long l, float f, double d) {
        return flag + "," + b + "," + c + "," + s + "," + i + "," + l + "," + f + "," + d;
    }

    public long returnsLong(long value) {
        return value + 1;
    }

    public boolean returnsBoolean(int value) {
        return value > 0;
    }

    public char returnsChar(String text) {
        return text.charAt(0);
    }

    public int[] squares(int count) {
        int[] result = new int[count];
        for (int i = 0; i < count; i++) {
            result[i] = i * i;
        }
        return result;
    }

    public String locals(String prefix) {
        StringBuilder builder = new StringBuilder(prefix);
        String suffix = "!";
        builder.append(suffix);
        String result = builder.toString();
        return result;
    }

    public String branches(int value) {
        if (value < 0) {
            return "negative";
        }
        if (value == 0) {
            return "zero";
        }
        return "positive";
    }

    public String tableSwitch(int value) {
        switch (value) {
            case 0:
                return "zero";
            case 1:
                return "one";
            case 2:
                return "two";
            default:
                return "many";
        }
    }

    public String lookupSwitch(int value) {
        switch (value) {
            case 10:
                return "ten";
            case 1000:
                return "thousand";
            case -5:
                return "minus five";
            default:
                return "other";
        }
    }

    public String stringSwitch(String value) {
        return switch (value) {
            case "red" -> "warm";
            case "blue" -> "cold";
            default -> "neutral";
        };
    }

    public int loop(int n) {
        int sum = 0;
        for (int i = 0; i < n; i++) {
            sum += i;
        }
        return sum;
    }

    public void fail(String message) {
        throw new IllegalStateException(message);
    }

    public String catchInside(String input) {
        try {
            Integer.parseInt(input);
            return "number";
        } catch (NumberFormatException e) {
            return "text";
        } finally {
            finallyCount++;
        }
    }

    public String rethrow(String input) throws Exception {
        try {
            throw new IOException(input);
        } catch (IOException e) {
            throw new Exception("wrapped " + e.getMessage(), e);
        }
    }

    public int lambda(List<Integer> values, int offset) {
        return values.stream().mapToInt(v -> v + offset).sum();
    }

    public synchronized String synchronizedMethod(String text) {
        return text + text;
    }

    public String synchronizedBlock(String text) {
        synchronized (this) {
            String doubled = text + text;
            return doubled;
        }
    }

    public String nested(String text) {
        return echo(text) + "|" + echo(text.toUpperCase());
    }

    private String hidden(String text) {
        return "[" + text + "]";
    }

    public String callsHidden(String text) {
        return hidden(text);
    }

    public String varargs(String... parts) {
        return String.join("+", parts);
    }

    public Object pattern(Object value) {
        if (value instanceof String s && !s.isEmpty()) {
            return s.length();
        }
        return value;
    }

    /**
     * Two locals in different scopes share one slot.
     */
    public String scopes(boolean flag) {
        if (flag) {
            String first = "first";
            return first;
        } else {
            String second = "second";
            return second;
        }
    }

    public String nullLocal() {
        String nothing = null;
        return String.valueOf(nothing);
    }

    public Object[][] multiArray(int a, int b) {
        Object[][] grid = new Object[a][b];
        grid[a - 1][b - 1] = "corner";
        return grid;
    }

    @Marker("method")
    public String annotated(@Marker("parameter") String text) {
        return text;
    }

    public static String staticMethod(String text) {
        staticCalls++;
        return text;
    }
}
