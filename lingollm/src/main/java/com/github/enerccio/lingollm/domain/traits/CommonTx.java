package com.github.enerccio.lingollm.domain.traits;

import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Transactional(CommonTx.QUALIFIER)
public @interface CommonTx {

    String QUALIFIER = "common";

}
