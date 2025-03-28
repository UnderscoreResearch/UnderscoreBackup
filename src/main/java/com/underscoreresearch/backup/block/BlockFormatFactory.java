package com.underscoreresearch.backup.block;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
public final class BlockFormatFactory {
    private static final Map<String, Class<? extends FileBlockExtractor>> blockFormats;

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

    public static FileBlockExtractor getExtractor(String format) {
        Class<? extends FileBlockExtractor> clz = blockFormats.get(format);
        if (clz == null)
            throw new IllegalArgumentException("Unsupported block format type \"" + format + "\"");
        return InstanceFactory.getInstance(clz);
    }
}
