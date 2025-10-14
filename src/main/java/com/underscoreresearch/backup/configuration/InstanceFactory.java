package com.underscoreresearch.backup.configuration;

import com.google.common.base.Strings;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.ProvisionException;
import com.google.inject.name.Names;
import com.underscoreresearch.backup.ui.ConfigurationValidator;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.implementation.LockingMetadataRepository;
import com.underscoreresearch.backup.model.BackupConfiguration;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.underscoreresearch.backup.configuration.CommandLineModule.SOURCE_CONFIG;
import static com.underscoreresearch.backup.io.IOProviderFactory.removeOldProviders;

/**
 * Factory for managing application instances and dependency injection.
 * This class provides centralized access to application components and manages
 * the lifecycle of the application.
 */
@Slf4j
public abstract class InstanceFactory {
    /**
     * Lock for controlling concurrent access to configuration.
     */
    private static final ReentrantReadWriteLock configReadWriteLock = new ReentrantReadWriteLock();
    
    /**
     * Read lock for accessing configuration.
     */
    private static final Lock configUseLock = configReadWriteLock.readLock();
    
    /**
     * Write lock for modifying configuration.
     */
    private static final Lock configChangeLock = configReadWriteLock.writeLock();
    
    /**
     * Reflections instance for scanning classes in the application package.
     */
    private static final Reflections REFLECTIONS = new Reflections("com.underscoreresearch.backup");
    
    /**
     * List of shutdown hooks to be executed in order.
     */
    private static final List<Runnable> shutdownHooks = new ArrayList<>();
    
    /**
     * Flag to prevent recursive cleanup operations.
     */
    private static final AtomicBoolean currentlyCleaningUp = new AtomicBoolean(false);
    
    /**
     * The default instance factory.
     */
    private static InstanceFactory defaultFactory;
    
    /**
     * Flag indicating if the application is shutting down.
     */
    @Getter
    private static boolean shutdown;
    
    /**
     * Initial command line arguments.
     */
    private static String[] initialArguments;
    
    /**
     * Cached configuration instance.
     */
    private static BackupConfiguration cachedConfig;
    
    /**
     * Flag indicating if the cached configuration is valid.
     */
    private static boolean cachedHasConfig;
    
    /**
     * Additional source identifier.
     */
    private static String additionalSource;

    /*
     * Static initializer to set up shutdown hook.
     */
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

    /**
     * Gets the Reflections instance for scanning classes in the application package.
     *
     * @return The Reflections instance
     */
    public static Reflections getReflections() {
        return REFLECTIONS;
    }

    /**
     * Checks if a valid configuration exists.
     *
     * @param readOnly Whether to validate in read-only mode
     * @return true if a valid configuration exists, false otherwise
     */
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

    /**
     * Gets the additional source identifier.
     *
     * @return The additional source identifier or null if none is set
     */
    public static String getAdditionalSource() {
        configUseLock.lock();
        try {
            return additionalSource;
        } finally {
            configUseLock.unlock();
        }
    }

    /**
     * Gets the additional source name.
     *
     * @return The additional source name or null if none is set
     */
    public static String getAdditionalSourceName() {
        String ret = getInstance(CommandLineModule.ADDITIONAL_SOURCE_NAME);
        if (Strings.isNullOrEmpty(ret)) {
            return null;
        }
        return ret;
    }

    /**
     * Initializes the instance factory with the provided command line arguments and source information.
     *
     * @param argv The command line arguments
     * @param source The source identifier (can be null)
     * @param sourceName The source name (can be null)
     */
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

    /**
     * Reloads the configuration using the current additional source.
     */
    public static void reloadConfigurationWithSource() {
        reloadConfiguration(InstanceFactory.getAdditionalSource(), InstanceFactory.getAdditionalSourceName(),
                null);
    }

    /**
     * Reloads the configuration and runs the provided startup code after reloading.
     *
     * @param startup The code to run after reloading the configuration
     */
    public static void reloadConfiguration(Runnable startup) {
        reloadConfiguration(null, null, startup);
    }

    /**
     * Reloads the configuration with the specified source and runs the provided startup code.
     *
     * @param source The source identifier (can be null)
     * @param sourceName The source name (can be null)
     * @param startup The code to run after reloading the configuration (can be null)
     */
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

    /**
     * Adds a cleanup hook that will be executed in order during shutdown.
     *
     * @param runnable The cleanup code to run
     */
    public static void addOrderedCleanupHook(Runnable runnable) {
        synchronized (shutdownHooks) {
            shutdownHooks.add(runnable);
        }
    }

    /**
     * Waits for the shutdown process to complete.
     */
    public static void waitForShutdown() {
        synchronized (shutdownHooks) {
        }
    }

    /**
     * Executes all registered cleanup hooks in order.
     */
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

    /**
     * Initializes the instance factory with the provided injector.
     *
     * @param injector The Guice injector
     */
    private static void initialize(Injector injector) {
        configUseLock.lock();
        try {
            defaultFactory = new DefaultFactory(injector);
        } finally {
            configUseLock.unlock();
        }
    }

    /**
     * Gets an instance of the specified class.
     *
     * @param tClass The class to get an instance of
     * @param <T> The type of the class
     * @return An instance of the specified class
     */
    public static <T> T getInstance(Class<T> tClass) {
        configUseLock.lock();
        try {
            return getFactory(tClass).instance(tClass);
        } finally {
            configUseLock.unlock();
        }
    }

    /**
     * Sets the application to shutdown state.
     */
    public static void shutdown() {
        shutdown = true;
    }

    /**
     * Gets a named instance of the specified class.
     *
     * @param name The name of the instance
     * @param tClass The class to get an instance of
     * @param <T> The type of the class
     * @return A named instance of the specified class
     */
    public static <T> T getInstance(String name, Class<T> tClass) {
        configUseLock.lock();
        try {
            return getFactory(tClass).instance(name, tClass);
        } finally {
            configUseLock.unlock();
        }
    }

    /**
     * Gets the factory for the specified class.
     *
     * @param tClass The class to get a factory for
     * @param <T> The type of the class
     * @return The factory for the specified class
     */
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

    /**
     * Checks if the instance factory has been initialized.
     *
     * @return true if the instance factory is initialized, false otherwise
     */
    public static boolean isInitialized() {
        configUseLock.lock();
        try {
            return defaultFactory != null;
        } finally {
            configUseLock.unlock();
        }
    }

    /**
     * Gets a named string instance.
     *
     * @param name The name of the string instance
     * @return The string instance
     */
    public static String getInstance(String name) {
        configUseLock.lock();
        try {
            return defaultFactory.instance(name, String.class);
        } finally {
            configUseLock.unlock();
        }
    }

    /**
     * Gets an instance of the specified class.
     * This method is implemented by concrete factory implementations.
     *
     * @param tClass The class to get an instance of
     * @param <T> The type of the class
     * @return An instance of the specified class
     */
    protected abstract <T> T instance(Class<T> tClass);

    /**
     * Gets a named instance of the specified class.
     * This method is implemented by concrete factory implementations.
     *
     * @param name The name of the instance
     * @param tClass The class to get an instance of
     * @param <T> The type of the class
     * @return A named instance of the specified class
     */
    protected abstract <T> T instance(String name, Class<T> tClass);

    /**
     * Default implementation of the InstanceFactory.
     */
    @AllArgsConstructor
    private static class DefaultFactory extends InstanceFactory {
        private Injector injector;

        /**
         * Gets an instance of the specified class.
         *
         * @param tClass The class to get an instance of
         * @param <T> The type of the class
         * @return An instance of the specified class
         */
        @Override
        protected <T> T instance(Class<T> tClass) {
            configUseLock.lock();
            try {
                return injector.getInstance(tClass);
            } finally {
                configUseLock.unlock();
            }
        }

        /**
         * Gets a named instance of the specified class.
         *
         * @param name The name of the instance
         * @param tClass The class to get an instance of
         * @param <T> The type of the class
         * @return A named instance of the specified class
         */
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
