package com.underscoreresearch.backup.block;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupFilePart;
import org.reflections.Reflections;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class BlockFormatFactory {
    private static Map<String, Class> blockFormats;

    static {
        blockFormats = new HashMap<>();

        Reflections reflections = InstanceFactory.getReflections();
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(BlockFormatPlugin.class);

        for (Class<?> clz : classes) {
            BlockFormatPlugin plugin = clz.getAnnotation(BlockFormatPlugin.class);
            blockFormats.put(plugin.value(), clz);
        }
    }

    public static FileBlockExtractor getExtractor(String format) {
        Class clz = blockFormats.get(format);
        if (clz == null)
            throw new IllegalArgumentException("Unsupported block format type " + format);
        return (FileBlockExtractor) InstanceFactory.getInstance(clz);
    }

    public static byte[] extractPart(BackupBlock block, BackupFilePart part, byte[] blockData) throws IOException {
        return getExtractor(block.getFormat()).extractPart(part, blockData);
    }
}
