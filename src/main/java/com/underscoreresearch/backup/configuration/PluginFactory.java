package com.underscoreresearch.backup.configuration;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PluginFactory {
    Class<InstanceFactory> factory();
}
