package com.underscoreresearch.backup.io;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for IO provider plugins.
 * Used to mark classes that implement IO providers and specify their type.
 * The value is used to identify the provider type in configuration.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface IOPlugin {
    /**
     * The type identifier for the IO plugin.
     * This value is used in configuration to specify which provider to use.
     *
     * @return The plugin type identifier
     */
    String value();
}
