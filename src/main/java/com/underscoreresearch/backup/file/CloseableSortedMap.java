package com.underscoreresearch.backup.file;

import java.util.Map;
import java.util.stream.Stream;

public interface CloseableSortedMap<K, V> extends CloseableMap<K, V> {

    Stream<Map.Entry<K, V>> readOnlyEntryStream(boolean ascending);
}
