package com.underscoreresearch.backup.cli;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CommandPlugin {
    String value();

    String args() default "";

    String description();

    boolean needPrivateKey() default true;

    boolean needConfiguration() default true;

    boolean readonlyRepository() default true;

    boolean supportSource() default false;
}
