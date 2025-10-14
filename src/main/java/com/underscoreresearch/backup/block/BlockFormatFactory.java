package com.underscoreresearch.backup.block;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Factory class for creating block format extractors based on registered plugins.
 * Uses reflection to discover and register block format plugins at runtime.
 */
@Slf4j
public final class BlockFormatFactory {
    private static final Map<String, Class<? extends FileBlockExtractor>> blockFormats;

    /**
     * Static initializer that discovers and registers all block format plugins.
     */
    static {
        blockFormats = new HashMap<>();

        Reflections reflections = InstanceFactory.getReflections();
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(BlockFormatPlugin.class);

        for (Class<?> untyped : classes) {
            try {
                @SuppressWarnings("unchecked")
                Class<? extends FileBlockExtractor> clz = (Class<FileBlockExtractor>) untyped;
                BlockFormatPlugin plugin = clz.getAnnotation(BlockFormatPlugin.class);
                blockFormats.put(plugin.value(), clz);
            } catch (ClassCastException exc) {
                log.error("Invalid type of class \"{}\"", untyped.getCanonicalName());
            }
        }
    }

    /**
     * Gets a block extractor instance for the specified format.
     * 
     * @param format The block format identifier
     * @return A FileBlockExtractor instance for the specified format
     * @throws IllegalArgumentException If the format is not supported
     */
    public static FileBlockExtractor getExtractor(String format) {
        Class<? extends FileBlockExtractor> clz = blockFormats.get(format);
        if (clz == null)
            throw new IllegalArgumentException("Unsupported block format type \"" + format + "\"");
        return InstanceFactory.getInstance(clz);
    }
}
