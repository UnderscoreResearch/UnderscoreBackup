package com.underscoreresearch.backup.io;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.model.BackupDestination;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

public final class IOProviderFactory {
    private static Map<String, Class> providerClasses;
    private static Map<BackupDestination, IOProvider> providers = new HashMap<>();

    static {
        providerClasses = new HashMap<>();

        Reflections reflections = InstanceFactory.getReflections();
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(IOPlugin.class);

        for (Class<?> clz : classes) {
            IOPlugin plugin = clz.getAnnotation(IOPlugin.class);
            providerClasses.put(plugin.value(), clz);
        }
    }

    public static List<String> supportedProviders() {
        List<String> ret = new ArrayList<>(providerClasses.keySet());
        ret.sort(String::compareTo);
        return ret;
    }

    public static void registerProvider(String type, Class provider) {
        providerClasses.put(type, provider);
    }

    public static synchronized IOProvider getProvider(BackupDestination destination) {
        IOProvider provider = providers.get(destination);
        if (provider != null) {
            return provider;
        }
        Class clz = providerClasses.get(destination.getType());
        if (clz == null)
            throw new IllegalArgumentException("Unsupported provider type " + destination.getType());

        try {
            Constructor constructor = clz.getConstructor(BackupDestination.class);
            provider = (IOProvider) constructor.newInstance(destination);
            providers.put(destination, provider);
            return provider;
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                InvocationTargetException e) {
            throw new IllegalArgumentException("Invalid provider type " + destination.getType(), e);
        }
    }
}