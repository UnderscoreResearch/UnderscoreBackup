package com.underscoreresearch.backup.file.implementation;

import com.google.common.collect.Lists;
import com.underscoreresearch.backup.io.IOIndex;
import com.underscoreresearch.backup.io.IOPlugin;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.model.BackupDestination;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;

@IOPlugin("MEMORY")
public class MemoryIOProvider implements IOProvider, IOIndex {
    private Map<String, byte[]> contents = new TreeMap<>();

    public MemoryIOProvider(BackupDestination destination) {
    }

    @Override
    public List<String> availableKeys(String prefix) throws IOException {
        if (!prefix.endsWith(PATH_SEPARATOR)) {
            prefix = prefix + PATH_SEPARATOR;
        }
        Set<String> ret = new TreeSet<>();
        for (Map.Entry<String, byte[]> entry : contents.entrySet()) {
            if (entry.getKey().startsWith(prefix) && entry.getKey().length() > prefix.length()) {
                String name = entry.getKey().substring(prefix.length());
                int ind = name.indexOf(PATH_SEPARATOR);
                if (ind >= 0) {
                    name = name.substring(0, ind + 1);
                }
                ret.add(name);
            }
        }
        return Lists.newArrayList(ret);
    }

    @Override
    public String upload(String suggestedKey, byte[] data) throws IOException {
        contents.put(suggestedKey, data);
        return suggestedKey;
    }

    @Override
    public byte[] download(String key) throws IOException {
        return contents.get(key);
    }

    @Override
    public String getCacheKey() {
        return "";
    }

    @Override
    public boolean exists(String key) throws IOException {
        return contents.containsKey(key);
    }

    @Override
    public void delete(String key) throws IOException {
        contents.remove(key);
    }

    @Override
    public void checkCredentials(boolean readonly) throws IOException {
    }
}
