package com.underscoreresearch.backup.block;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for marking classes as block format plugins.
 * Used by the BlockFormatFactory to discover and register block format implementations.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface BlockFormatPlugin {
    /**
     * The identifier for this block format plugin.
     * 
     * @return The string identifier for the block format
     */
    String value();
}
