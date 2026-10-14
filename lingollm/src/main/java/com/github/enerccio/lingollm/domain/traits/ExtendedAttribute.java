package com.github.enerccio.lingollm.domain.traits;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ExtendedAttribute {

    boolean inject() default false;
    String injectPrefix() default "";

}
