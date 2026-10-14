package com.github.enerccio.lingollm.instruct.verify;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.Value;

import java.util.Arrays;

/**
 * What the verifier knows about a value in the bytecode of a decorator: constants, the instrumented object, and where
 * an object came from. Everything else is {@link Unknown}.
 */
sealed interface ContextValue extends Value {

    @Override
    default int getSize() {
        return 1;
    }

    /**
     * A value nothing is known about, {@code size} 2 for long and double.
     */
    record Unknown(int size) implements ContextValue {
        @Override
        public int getSize() {
            return size;
        }
    }

    record Str(String value) implements ContextValue {
    }

    /**
     * A class literal, primitive types included ({@code int.class} is a read of {@code Integer.TYPE}).
     */
    record Cls(Type type) implements ContextValue {
    }

    record Int(int value) implements ContextValue {
    }

    /**
     * The {@code instrumented} argument of {@code onMethodEnter} / {@code onMethodLeave}.
     */
    record Instrumented() implements ContextValue {
    }

    /**
     * An object that is at least of this type: created with {@code new}, or cast.
     */
    record Typed(Type type) implements ContextValue {
    }

    /**
     * The result of {@code getReflectiveFieldValue(owner, field, ...)}: the object in that field of {@code owner}.
     */
    record FieldValue(ContextValue owner, String field) implements ContextValue {
    }

    /**
     * The result of {@code callReflectiveMethod(owner, method, argumentTypes, ...)}.
     */
    record MethodValue(ContextValue owner, String method, ContextValue argumentTypes) implements ContextValue {
    }

    /**
     * A value read from a field of the extension itself, which is how a decorator built earlier reaches
     * {@code registerDecorator}.
     */
    record Member(String owner, String name) implements ContextValue {
    }

    /**
     * An array created with a constant length. Mutable (and compared by identity), the elements are stored as the
     * array is filled in.
     */
    final class Array implements ContextValue {

        private final ContextValue[] elements;

        Array(int length) {
            this.elements = new ContextValue[Math.max(length, 0)];
        }

        void set(int index, ContextValue value) {
            if (index >= 0 && index < elements.length) {
                elements[index] = value;
            }
        }

        ContextValue get(int index) {
            return index >= 0 && index < elements.length ? elements[index] : null;
        }

        int length() {
            return elements.length;
        }

        @Override
        public String toString() {
            return Arrays.toString(elements);
        }
    }

    Unknown ONE = new Unknown(1);
    Unknown TWO = new Unknown(2);
    Instrumented INSTRUMENTED = new Instrumented();

    static Unknown unknown(int size) {
        return size == 2 ? TWO : ONE;
    }
}
