package com.github.enerccio.tools;

import java.util.Objects;

public class Pair<T1, T2> {

    private final T1 a;
    private final T2 b;

    private Pair(T1 a, T2 b) {
        this.a = a;
        this.b = b;
    }

    public T1 getA() {
        return a;
    }

    public T2 getB() {
        return b;
    }

    @Override
    public String toString() {
        return "Pair{" +
                "a=" + a +
                ", b=" + b +
                '}';
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Pair<?, ?> pair)) return false;
        return Objects.equals(getA(), pair.getA()) && Objects.equals(getB(), pair.getB());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getA(), getB());
    }

    public static <X, Y> Pair<X, Y> of(X x, Y y) {
        return new Pair<>(x, y);
    }

}
