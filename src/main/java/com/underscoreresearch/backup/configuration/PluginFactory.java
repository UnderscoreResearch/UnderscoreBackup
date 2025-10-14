package com.underscoreresearch.backup.configuration;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for marking classes that should be instantiated through a specific factory.
 * This is used to customize the instantiation process for certain components.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PluginFactory {
    /**
     * The factory class to use for instantiating the annotated class.
     * 
     * @return The factory class
     */
    Class<InstanceFactory> factory();
}
