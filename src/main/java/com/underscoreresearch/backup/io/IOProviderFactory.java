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
        for (Map.Entry<BackupDestination, IOProvider> entry : providers.entrySet()) {
            if (entry.getValue() instanceof Closeable) {
                try {
                    ((Closeable) entry.getValue()).close();
                } catch (IOException e) {
                    log.error("Failed to close IO provider for {}", entry.getKey().getEndpointUri());
                }
            }
        }

        providers = new HashMap<>();
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
            provider = readOnlyOnSource(constructor.newInstance(destination));
            providers.put(destination, provider);
            return provider;
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException e) {
            throw new IllegalArgumentException("Invalid provider type " + destination.getType(), e);
        }
    }

    private static IOProvider readOnlyOnSource(IOProvider actualProvider) {
        if (InstanceFactory.getAdditionalSource() != null) {
            if (actualProvider instanceof IOIndex) {
                return new IOIndex() {
                    @Override
                    public List<String> availableKeys(String prefix) throws IOException {
                        return ((IOIndex) actualProvider).availableKeys(prefix);
                    }

                    @Override
                    public String upload(String suggestedKey, byte[] data) throws IOException {
                        throw new IOException(String.format("Cant upload data to %s", InstanceFactory.getAdditionalSource()));
                    }

                    @Override
                    public byte[] download(String key) throws IOException {
                        return actualProvider.download(key);
                    }

                    @Override
                    public void delete(String key) throws IOException {
                        throw new IOException(String.format("Cant delete data from %s", InstanceFactory.getAdditionalSource()));
                    }

                    @Override
                    public void checkCredentials(boolean readOnly) throws IOException {
                        actualProvider.checkCredentials(readOnly);
                    }
                };
            } else {
                return new IOProvider() {
                    @Override
                    public String upload(String suggestedKey, byte[] data) throws IOException {
                        throw new IOException(String.format("Cant upload data to %s", InstanceFactory.getAdditionalSource()));
                    }

                    @Override
                    public byte[] download(String key) throws IOException {
                        return actualProvider.download(key);
                    }

                    @Override
                    public void delete(String key) throws IOException {
                        throw new IOException(String.format("Cant delete data from %s", InstanceFactory.getAdditionalSource()));
                    }

                    @Override
                    public void checkCredentials(boolean readOnly) throws IOException {
                        actualProvider.checkCredentials(readOnly);
                    }
                };
            }
        } else {
            return actualProvider;
        }
    }
}
