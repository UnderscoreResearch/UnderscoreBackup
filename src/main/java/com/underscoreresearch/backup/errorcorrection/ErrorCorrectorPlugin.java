package com.underscoreresearch.backup.errorcorrection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for marking error corrector implementations.
 * Classes annotated with this will be automatically discovered
 * and registered by the ErrorCorrectorFactory.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ErrorCorrectorPlugin {
    /**
     * The name of the error corrector implementation.
     * This value is used as the key to identify the error corrector type.
     *
     * @return The name of the error corrector
     */
    String value();
}
