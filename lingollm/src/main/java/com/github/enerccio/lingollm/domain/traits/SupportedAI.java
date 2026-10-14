package com.github.enerccio.lingollm.domain.traits;

import com.github.enerccio.lingollm.domain.collections.AIType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SupportedAI {
    AIType value();
}