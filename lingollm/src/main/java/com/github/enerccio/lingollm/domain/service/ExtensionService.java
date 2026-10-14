package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.instruct.verify.ExtensionVerification;
import org.osgi.framework.Bundle;

import java.io.File;

public interface ExtensionService {

    ExtendableMethodContext createContextHolder(Object owner);

    void registerDecorator(ExtensionDecorator decorator, String className, String methodName) throws Exception;
    void unregisterDecorator(ExtensionDecorator decorator);

    /**
     * Checks that the extension fits this version of the application, without running it. The classes of the
     * extension are scanned (the classes of {@code jar} are read from the installed {@code bundle}); every
     * {@code registerDecorator} call is followed to the class and method it decorates, and the registered decorator is
     * checked for what it asks from its {@link ExtendableMethodContext} - arguments, local variables, fields and
     * methods - against the real classes of the application. Only the decorators themselves are scanned: when a
     * context is handed over to another class, nothing is checked there.
     * <p>
     * The report is written next to the JAR, named after it: {@code name.valid} or {@code name.invalid} (the one of
     * the opposite result is deleted).
     *
     * @return the result, {@link ExtensionVerification#valid()} is false when the extension asks for something that
     * does not exist
     */
    ExtensionVerification verifyExtension(Bundle bundle, File jar) throws Exception;

    void onExtendableMethodEnter(Class<?> cls, Object extendableSelf, ExtendableMethodContext context, String method);
    void onExtendableMethodLeave(Class<?> cls, Object extendableSelf, ExtendableMethodContext context, String method, Throwable throwing);

    interface ExtendableMethodContext {

        void registerLocalVariable(String name, Object value, Class<?> type);
        <T> T getLocalVariable(String name, Class<T> type) throws Exception;
        boolean hasLocalVariable(String name);
        boolean hasLocalVariable(String name, Class<?> isOfType);
        <T> T getReflectiveFieldValue(Object object, String field, Class<T> fieldReturnType);
        <T> void setReflectiveFieldValue(Object object, String field, T value, Class<T> fieldReturnType);
        MethodArgumentRefresher registerMethodArgument(String name, Object value, Class<?> type);
        <T> T getMethodArgument(String name, Class<T> type) throws Exception;
        boolean hasMethodArgument(String name);
        boolean hasMethodArgument(String name, Class<?> isOfType);
        <T> T callReflectiveMethod(Object object, String methodName, Class<?>[] argumentTypes, Object[] arguments, Class<T> returnType);

        /**
         * Called by the instrumented method when it returns a value, before the decorators run: {@code value} is what
         * the body returned (primitives boxed), {@code type} the declared return type ({@code int.class} for
         * {@code int}). Not called by {@code void} methods and when the method throws.
         */
        void registerReturnValue(Object value, Class<?> type);

        /**
         * Called by the instrumented method after the decorators ran: the value to return, the one the last decorator
         * left with {@link #setCurrentReturn(Object)}, the original one if none did.
         */
        Object resolveReturnValue();

        /**
         * Whether the method returned a value: false in {@code onMethodEnter}, for a {@code void} method and when the
         * method throws.
         */
        boolean hasReturn();

        /**
         * What the body of the method returned, whatever the decorators did. The type is checked against the declared
         * return type of the method like the type of an argument (primitives: {@code int.class}).
         */
        <T> T getOriginalReturn(Class<T> type);

        /**
         * What the method returns as of now: the original value, or the value set by the decorator that ran before
         * (decorators on the same method are chained in the order they were registered).
         */
        <T> T getCurrentReturn(Class<T> type);

        /**
         * Replaces the value the method returns (the decorators that follow see it as the current return value). The
         * value has to fit the declared return type, a primitive one cannot be {@code null}; otherwise the call is
         * refused and the current return value stays.
         */
        void setCurrentReturn(Object value);
    }

    interface MethodArgumentRefresher {
        Object get();
    }

    interface ExtensionDecorator {

        void onMethodEnter(Object instrumented, ExtendableMethodContext context) throws Exception;
        void onMethodLeave(Object instrumented, ExtendableMethodContext context, Throwable throwing) throws Exception;

    }



}
