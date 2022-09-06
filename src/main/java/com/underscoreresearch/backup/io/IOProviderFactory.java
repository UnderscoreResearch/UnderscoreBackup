package com.underscoreresearch.backup.io;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import org.reflections.Reflections;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.model.BackupConfiguration;
import com.underscoreresearch.backup.model.BackupDestination;

@Slf4j
public final class IOProviderFactory {
    private static Map<String, Class<? extends IOProvider>> providerClasses;
    private static Map<BackupDestination, IOProvider> providers = new HashMap<>();

    static {
        providerClasses = new HashMap<>();

        Reflections reflections = InstanceFactory.getReflections();
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(IOPlugin.class);

        for (Class<?> untyped : classes) {
            @SuppressWarnings("unchecked")
            Class<IOProvider> clz = (Class<IOProvider>) untyped;
            IOPlugin plugin = clz.getAnnotation(IOPlugin.class);
            providerClasses.put(plugin.value(), clz);
        }
    }

    public static List<String> supportedProviders() {
        List<String> ret = new ArrayList<>(providerClasses.keySet());
        ret.sort(String::compareTo);
        return ret;
    }

    public static void removeOldProviders() {
        BackupConfiguration configuration = InstanceFactory.getInstance(BackupConfiguration.class);

        Map<BackupDestination, IOProvider> newProviders = new HashMap<>();
        if (configuration.getDestinations() != null) {
            for (Map.Entry<String, BackupDestination> entry : configuration.getDestinations().entrySet()) {
                IOProvider provider = providers.remove(entry.getValue());
                if (provider != null) {
                    newProviders.put(entry.getValue(), provider);
                }
            }
        }

        for (Map.Entry<BackupDestination, IOProvider> entry : providers.entrySet()) {
            if (entry.getValue() instanceof Closeable) {
                try {
                    ((Closeable) entry.getValue()).close();
                } catch (IOException e) {
                    log.error("Failed to close IO provider for {}", entry.getKey().getEndpointUri());
                }
            }
        }

        providers = newProviders;
    }

    public static void registerProvider(String type, Class<? extends IOProvider> provider) {
        providerClasses.put(type, provider);
    }

    public static synchronized IOProvider getProvider(BackupDestination destination) {
        IOProvider provider = providers.get(destination);
        if (provider != null) {
            return provider;
        }
        Class<? extends IOProvider> clz = providerClasses.get(destination.getType());
        if (clz == null)
            throw new IllegalArgumentException("Unsupported provider type " + destination.getType());

        try {
            Constructor<? extends IOProvider> constructor = clz.getConstructor(BackupDestination.class);
            provider = constructor.newInstance(destination);
            providers.put(destination, provider);
            return provider;
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException e) {
            throw new IllegalArgumentException("Invalid provider type " + destination.getType(), e);
        }
    }
}
