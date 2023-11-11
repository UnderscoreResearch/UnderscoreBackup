package com.underscoreresearch.backup.errorcorrection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import org.reflections.Reflections;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.model.BackupBlockStorage;

@Slf4j
public final class ErrorCorrectorFactory {
    private static final Map<String, Class<? extends ErrorCorrector>> correctors;

    static {
        correctors = new HashMap<>();

        Reflections reflections = InstanceFactory.getReflections();
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(ErrorCorrectorPlugin.class);

        for (Class<?> untyped : classes) {
            @SuppressWarnings("unchecked")
            Class<? extends ErrorCorrector> clz = (Class<ErrorCorrector>) untyped;
            ErrorCorrectorPlugin plugin = clz.getAnnotation(ErrorCorrectorPlugin.class);
            correctors.put(plugin.value(), clz);
        }
    }

    public static List<String> supportedCorrectors() {
        List<String> ret = new ArrayList<>(correctors.keySet());
        ret.sort(String::compareTo);
        return ret;
    }

    public static boolean hasCorrector(String ec) {
        Class<? extends ErrorCorrector> clz = correctors.get(ec);
        if (clz == null)
            return false;
        return true;
    }

    public static ErrorCorrector getCorrector(String ec) {
        Class<? extends ErrorCorrector> clz = correctors.get(ec);
        if (clz == null)
            throw new IllegalArgumentException("Unsupported error correction type \"" + ec + "\"");
        return InstanceFactory.getInstance(clz);
    }

    public static List<byte[]> encodeBlocks(String ec, BackupBlockStorage storage,
                                            byte[] data) throws Exception {
        return getCorrector(ec).encodeErrorCorrection(storage, data);
    }

    public static byte[] decodeBlock(BackupBlockStorage storage, List<byte[]> parts)
            throws Exception {
        return getCorrector(storage.getEncryption()).decodeErrorCorrection(storage, parts);
    }
}
