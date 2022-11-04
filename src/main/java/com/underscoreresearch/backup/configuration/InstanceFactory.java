package com.underscoreresearch.backup.configuration;

import static com.underscoreresearch.backup.configuration.CommandLineModule.SOURCE_CONFIG;
import static com.underscoreresearch.backup.io.IOProviderFactory.removeOldProviders;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.reflections.Reflections;

import com.google.common.base.Strings;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.ProvisionException;
import com.google.inject.name.Names;
import com.underscoreresearch.backup.cli.ConfigurationValidator;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.model.BackupConfiguration;

@Slf4j
public abstract class InstanceFactory {
    private static InstanceFactory defaultFactory;
    private static Reflections reflections = new Reflections("com.underscoreresearch.backup");
    private static boolean shutdown;
    private static String[] initialArguments;
    private static List<Runnable> shutdownHooks = new ArrayList<>();
    private static BackupConfiguration cachedConfig;
    private static boolean cachedHasConfig;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            executeOrderedCleanupHook();
        }));
    }

    public static Reflections getReflections() {
        return reflections;
    }

    public static synchronized boolean hasConfiguration(boolean readOnly) {
        try {
            BackupConfiguration config = InstanceFactory.getInstance(SOURCE_CONFIG, BackupConfiguration.class);
            if (cachedConfig == config) {
                return cachedHasConfig;
            }
            if (config.getManifest() == null && config.getDestinations() == null) {
                cachedHasConfig = false;
            } else {
                cachedConfig = config;
                ConfigurationValidator.validateConfiguration(config,
                        readOnly, InstanceFactory.getAdditionalSource() != null);
                cachedHasConfig = true;
            }
        } catch (ProvisionException exc) {
            cachedHasConfig = false;
        }
        return cachedHasConfig;
    }

    public static String getAdditionalSource() {
        String ret = getInstance(CommandLineModule.ADDITIONAL_SOURCE);
        if (Strings.isNullOrEmpty(ret)) {
            return null;
        }
        return ret;
    }

    public static void initialize(String[] argv, String source) {
        initialArguments = argv;
        shutdown = false;
        initialize(Guice.createInjector(
                new CommandLineModule(argv, source),
                new EncryptionModule(),
                new ErrorCorrectionModule(),
                new BackupModule(),
                new RestoreModule()));
    }

    public static void reloadConfiguration(String source) {
        reloadConfiguration(source, null);
    }

    public static void reloadConfiguration(String source, Runnable startup) {
        synchronized (shutdownHooks) {
            executeOrderedCleanupHook();
            MetadataRepository repository = null;
            try {
                repository = InstanceFactory.getInstance(MetadataRepository.class);
            } catch (ProvisionException e) {
            }
            try {
                if (repository != null) {
                    repository.close();
                }
            } catch (IOException e) {
                log.error("Failed to close metadata repository");
            }
            initialize(initialArguments, source);
            if (hasConfiguration(true)) {
                ConfigurationValidator.validateConfiguration(
                        getInstance(SOURCE_CONFIG, BackupConfiguration.class),
                        true,
                        source != null);
            }
            removeOldProviders();

            if (startup != null) {
                startup.run();
            }
        }
    }

    public static void addOrderedCleanupHook(Runnable runnable) {
        synchronized (shutdownHooks) {
            shutdownHooks.add(runnable);
        }
    }

    public static void waitForShutdown() {
        synchronized (shutdownHooks) {
        }
    }

    private static void executeOrderedCleanupHook() {
        synchronized (shutdownHooks) {
            for (Runnable shutdown : shutdownHooks) {
                try {
                    shutdown.run();
                } catch (Exception exc) {
                    log.error("Failed to run shutdown hook", exc);
                }
            }
            shutdownHooks.clear();
        }
    }

    private static void initialize(Injector injector) {
        defaultFactory = new DefaultFactory(injector);
    }

    public static <T> T getInstance(Class<T> tClass) {
        return getFactory(tClass).instance(tClass);
    }

    public static boolean isShutdown() {
        return shutdown;
    }

    public static void shutdown() {
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
}
