package com.underscoreresearch.backup.configuration;

import static com.underscoreresearch.backup.configuration.CommandLineModule.SOURCE_CONFIG;
import static com.underscoreresearch.backup.io.IOProviderFactory.removeOldProviders;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
import com.underscoreresearch.backup.file.implementation.LockingMetadataRepository;
import com.underscoreresearch.backup.model.BackupConfiguration;

@Slf4j
public abstract class InstanceFactory {
    private static final ReentrantReadWriteLock configReadWriteLock = new ReentrantReadWriteLock();
    private static final Lock configUseLock = configReadWriteLock.readLock();
    private static final Lock configChangeLock = configReadWriteLock.writeLock();
    private static final Reflections REFLECTIONS = new Reflections("com.underscoreresearch.backup");
    private static final List<Runnable> shutdownHooks = new ArrayList<>();
    private static final AtomicBoolean currentlyCleaningUp = new AtomicBoolean(false);
    private static InstanceFactory defaultFactory;
    private static boolean shutdown;
    private static String[] initialArguments;
    private static BackupConfiguration cachedConfig;
    private static boolean cachedHasConfig;
    private static String additionalSource;

    static {
        Thread thread = new Thread(() -> {
            executeOrderedCleanupHook();

            LockingMetadataRepository.closeAllRepositories();

            System.out.close();
            System.err.close();

            Thread watchdogThread = new Thread(() -> {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                log.error("Failed to shut down gracefully, exiting forcefully");
                Runtime.getRuntime().halt(1);
            }, "ShutdownWatchdog");
            watchdogThread.setDaemon(true);
            watchdogThread.start();

        }, "ShutdownHook");

        thread.setDaemon(true);
        Runtime.getRuntime().addShutdownHook(thread);
    }

    public static Reflections getReflections() {
        return REFLECTIONS;
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
        configUseLock.lock();
        try {
            return additionalSource;
        } finally {
            configUseLock.unlock();
        }
    }

    public static String getAdditionalSourceName() {
        String ret = getInstance(CommandLineModule.ADDITIONAL_SOURCE_NAME);
        if (Strings.isNullOrEmpty(ret)) {
            return null;
        }
        return ret;
    }

    public static void initialize(String[] argv, String source, String sourceName) {
        configChangeLock.lock();
        try {
            initialArguments = argv;
            initialize(Guice.createInjector(
                    new CommandLineModule(argv, source, sourceName),
                    new EncryptionModule(),
                    new ErrorCorrectionModule(),
                    new BackupModule(),
                    new RestoreModule()));

            additionalSource = getInstance(CommandLineModule.ADDITIONAL_SOURCE);
            if (Strings.isNullOrEmpty(additionalSource)) {
                additionalSource = null;
            }

            shutdown = false;
        } finally {
            configChangeLock.unlock();
        }
    }

    public static void reloadConfigurationWithSource() {
        reloadConfiguration(InstanceFactory.getAdditionalSource(), InstanceFactory.getAdditionalSourceName(),
                null);
    }

    public static void reloadConfiguration(Runnable startup) {
        reloadConfiguration(null, null, startup);
    }

    public static void reloadConfiguration(String source, String sourceName, Runnable startup) {
        synchronized (shutdownHooks) {
            if (currentlyCleaningUp.get()) {
                throw new IllegalStateException("Cannot reload configuration recursively");
            }
            currentlyCleaningUp.set(true);
            try {
                executeOrderedCleanupHook();
                MetadataRepository repository = null;
                try {
                    repository = InstanceFactory.getInstance(MetadataRepository.class);
                } catch (ProvisionException ignored) {
                }
                try {
                    if (repository != null) {
                        repository.close();
                    }
                } catch (IOException e) {
                    log.error("Failed to close metadata repository");
                }
                initialize(initialArguments, source, sourceName);
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
            } finally {
                currentlyCleaningUp.set(false);
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
        configUseLock.lock();
        try {
            defaultFactory = new DefaultFactory(injector);
        } finally {
            configUseLock.unlock();
        }
    }

    public static <T> T getInstance(Class<T> tClass) {
        configUseLock.lock();
        try {
            return getFactory(tClass).instance(tClass);
        } finally {
            configUseLock.unlock();
        }
    }

    public static boolean isShutdown() {
        return shutdown;
    }

    public static void shutdown() {
        shutdown = true;
    }

    public static <T> T getInstance(String name, Class<T> tClass) {
        configUseLock.lock();
        try {
            return getFactory(tClass).instance(name, tClass);
        } finally {
            configUseLock.unlock();
        }
    }

    public static <T> InstanceFactory getFactory(Class<T> tClass) {
        configUseLock.lock();
        try {
            PluginFactory ret = tClass.getAnnotation(PluginFactory.class);
            if (ret != null && ret.factory() != null) {
                return defaultFactory.instance(ret.factory());
            }
            return defaultFactory;
        } finally {
            configUseLock.unlock();
        }
    }

    public static boolean isInitialized() {
        configUseLock.lock();
        try {
            return defaultFactory != null;
        } finally {
            configUseLock.unlock();
        }
    }

    public static String getInstance(String name) {
        configUseLock.lock();
        try {
            return defaultFactory.instance(name, String.class);
        } finally {
            configUseLock.unlock();
        }
    }

    protected abstract <T> T instance(Class<T> tClass);

    protected abstract <T> T instance(String name, Class<T> tClass);

    @AllArgsConstructor
    private static class DefaultFactory extends InstanceFactory {
        private Injector injector;

        @Override
        protected <T> T instance(Class<T> tClass) {
            configUseLock.lock();
            try {
                return injector.getInstance(tClass);
            } finally {
                configUseLock.unlock();
            }
        }

        @Override
        protected <T> T instance(String name, Class<T> tClass) {
            configUseLock.lock();
            try {
                return injector.getInstance(Key.get(tClass, Names.named(name)));
            } finally {
                configUseLock.unlock();
            }
        }
    }
}
