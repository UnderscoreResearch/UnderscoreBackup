package com.underscoreresearch.backup.errorcorrection;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.model.BackupBlockStorage;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Factory class for creating and managing error correctors.
 * This class discovers and instantiates error corrector implementations
 * based on their ErrorCorrectorPlugin annotations.
 */
@Slf4j
public final class ErrorCorrectorFactory {
    /**
     * Map of error corrector type names to their implementing classes.
     */
    private static final Map<String, Class<? extends ErrorCorrector>> correctors;

    /*
     * Static initializer that discovers and registers all error corrector implementations.
     */
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

    /**
     * Gets a sorted list of all supported error corrector types.
     *
     * @return A sorted list of error corrector type names
     */
    public static List<String> supportedCorrectors() {
        List<String> ret = new ArrayList<>(correctors.keySet());
        ret.sort(String::compareTo);
        return ret;
    }

    /**
     * Checks if a specific error corrector type is supported.
     *
     * @param ec The error corrector type name
     * @return true if the error corrector is supported, false otherwise
     */
    public static boolean hasCorrector(String ec) {
        Class<? extends ErrorCorrector> clz = correctors.get(ec);
        return clz != null;
    }

    /**
     * Gets an instance of the specified error corrector.
     *
     * @param ec The error corrector type name
     * @return An instance of the requested error corrector
     * @throws IllegalArgumentException If the requested error corrector is not supported
     */
    public static ErrorCorrector getCorrector(String ec) {
        Class<? extends ErrorCorrector> clz = correctors.get(ec);
        if (clz == null)
            throw new IllegalArgumentException("Unsupported error correction type \"" + ec + "\"");
        return InstanceFactory.getInstance(clz);
    }

    /**
     * Encodes data using the specified error corrector.
     *
     * @param ec The error corrector type name
     * @param storage The backup block storage containing error correction parameters
     * @param data The data to encode
     * @return A list of encoded parts
     * @throws Exception If there's an error during encoding
     */
    public static List<byte[]> encodeBlocks(String ec, BackupBlockStorage storage,
                                            byte[] data) throws Exception {
        return getCorrector(ec).encodeErrorCorrection(storage, data);
    }

    /**
     * Decodes data using the error corrector specified in the storage.
     *
     * @param storage The backup block storage containing error correction parameters
     * @param parts The list of available parts for decoding
     * @return The decoded data
     * @throws Exception If there's an error during decoding
     */
    public static byte[] decodeBlock(BackupBlockStorage storage, List<byte[]> parts)
            throws Exception {
        return getCorrector(storage.getEncryption()).decodeErrorCorrection(storage, parts);
    }
}
