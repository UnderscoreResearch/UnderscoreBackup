package com.underscoreresearch.backup.configuration;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import lombok.AllArgsConstructor;
import org.reflections.Reflections;

public abstract class InstanceFactory {
    private static InstanceFactory defaultFactory;
    private static Reflections reflections = new Reflections("com.underscoreresearch.backup");
    private static boolean shutdown;


    public static Reflections getReflections() {
        return reflections;
    }

    protected abstract <T> T instance(Class<T> tClass);

    protected abstract <T> T instance(String name, Class<T> tClass);

    @AllArgsConstructor
    private static class DefaultFactory extends InstanceFactory {
        private Injector injector;

        @Override
        protected <T> T instance(Class<T> tClass) {
            return injector.getInstance(tClass);
        }

        @Override
        protected <T> T instance(String name, Class<T> tClass) {
            return injector.getInstance(Key.get(tClass, Names.named(name)));
        }
    }

    public static void initialize(String[] argv) {
        initialize(Guice.createInjector(
                new CommandLineModule(argv),
                new EncryptionModule(),
                new ErrorCorrectionModule(),
                new BackupModule(),
                new RestoreModule()));
    }

    public static void initialize(Injector injector) {
        defaultFactory = new DefaultFactory(injector);
    }

    public static <T> T getInstance(Class<T> tClass) {
        return getFactory(tClass).instance(tClass);
    }

    public static boolean shutdown() {
        return shutdown;
    }

    public static void shutdown(boolean shutdown) {
        InstanceFactory.shutdown = true;
    }

    public static <T> T getInstance(String name, Class<T> tClass) {
        return getFactory(tClass).instance(name, tClass);
    }

    public static <T> InstanceFactory getFactory(Class<T> tClass) {
        PluginFactory ret = tClass.getAnnotation(PluginFactory.class);
        if (ret != null && ret.factory() != null) {
            return defaultFactory.instance(ret.factory());
        }
        return defaultFactory;
    }

    public static String getInstance(String name) {
        return defaultFactory.instance(name, String.class);
    }
}
