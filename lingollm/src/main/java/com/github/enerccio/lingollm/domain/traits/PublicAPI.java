package com.github.enerccio.lingollm.domain.traits;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the signature of a method of an {@link Extendable} class that plugins may decorate, as the plugins see it
 * through {@code ExtendableMethodContext}: the name and type of every argument, the type of the result and the name
 * and type of every named object local variable. Every instrumented method of an {@code @Extendable} class (not a
 * constructor, not static, not synthetic) carries it; {@code PublicAPISignatureTest} compares the annotation with the
 * compiled class and fails when they differ, so renaming an argument or a local variable, changing a type or adding a
 * method cannot go unnoticed - each of them can break a plugin.
 *
 * <p>When a method changes in a way a plugin can notice, update the annotation, set {@link #from()} to the version
 * that contains the change and check the plugins in {@code lingollm/plugins}.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PublicAPI {

    /**
     * The first version of the application in which the method has exactly this signature, for example
     * {@code "1.2.0"}.
     */
    String from();

    /**
     * Declared return type ({@code void.class} for {@code void}).
     */
    APIReturn returns();

    /**
     * All arguments, in declaration order.
     */
    APIParameter[] parameters() default {};

    /**
     * The named local variables holding an object (primitive locals are not visible to decorators) that are not
     * arguments, in the order of the local variable table. A name used for more than one type appears once per type.
     */
    APILocals[] locals() default {};
}
