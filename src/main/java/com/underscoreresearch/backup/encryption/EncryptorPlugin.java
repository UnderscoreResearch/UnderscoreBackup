package com.underscoreresearch.backup.encryption;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for marking classes as encryption plugins.
 * This annotation is used to discover and register encryption implementations
 * at runtime through reflection.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EncryptorPlugin {
    /**
     * The identifier for this encryption plugin.
     * 
     * @return The encryption type identifier
     */
    String value();

    /**
     * Indicates whether this encryption plugin requires storage metadata.
     * 
     * @return True if storage metadata is required, false otherwise
     */
    boolean requireStorage() default false;
}
