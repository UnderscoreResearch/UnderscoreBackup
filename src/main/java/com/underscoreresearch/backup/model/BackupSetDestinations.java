package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Set;

import static com.underscoreresearch.backup.io.IOUtils.createDirectory;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Slf4j
public class BackupSetDestinations {
    private static final ObjectWriter WRITER = MAPPER.writerFor(BackupSetDestinations.class);
    private static final ObjectReader READER = MAPPER.readerFor(BackupSetDestinations.class);

    private boolean initial;
    private Set<String> minUsedDestinations;
    private Set<String> completedDestinations;
    private boolean consistent;

    private static File backupSetLocationInfo(String manifestLocation, BackupSet backupSet) {
        File file = Paths.get(manifestLocation, "db", "sets",
                backupSet.getId() + ".json").toFile();
        createDirectory(file.getParentFile(), true);
        return file;
    }

    public static boolean needStorageValidation(String manifestLocation, BackupSet backupSet,
                                                boolean initial) throws IOException {
        File file = backupSetLocationInfo(manifestLocation, backupSet);
        Set<String> destinations = Sets.newHashSet(backupSet.getDestinations());
        if (file.exists()) {
            try {
                BackupSetDestinations sets = READER.readValue(file);
                if (initial) {
                    sets.initial = true;
                    sets.minUsedDestinations = destinations;
                    if (sets.getCompletedDestinations() != null)
                        sets.consistent = sets.getCompletedDestinations().containsAll(destinations);
                    WRITER.writeValue(file, sets);
                } else {
                    sets.minUsedDestinations.retainAll(backupSet.getDestinations());
                    if (sets.consistent && !sets.minUsedDestinations.containsAll(destinations)) {
                        sets.consistent = false;
                        WRITER.writeValue(file, sets);
                    }
                }
                return !sets.consistent;
            } catch (IOException e) {
                log.warn("Error reading backup set destinations, resetting", e);
            }
        }
        BackupSetDestinations sets = BackupSetDestinations.builder().consistent(false)
                .minUsedDestinations(destinations)
                .initial(initial)
                .build();
        WRITER.writeValue(file, sets);
        return true;
    }

    public static void completedStorageValidation(String manifestLocation, BackupSet backupSet) throws IOException {
        File file = backupSetLocationInfo(manifestLocation, backupSet);
        BackupSetDestinations sets;
        try {
            sets = READER.readValue(file);
        } catch (IOException e) {
            log.warn("Error reading backup set destinations, resetting", e);
            sets = BackupSetDestinations.builder().consistent(false)
                    .minUsedDestinations(Sets.newHashSet(backupSet.getDestinations()))
                    .initial(true)
                    .build();
        }
        if (sets.minUsedDestinations.containsAll(backupSet.getDestinations()) && sets.initial) {
            sets.completedDestinations = sets.minUsedDestinations;
            sets.consistent = true;
        } else {
            sets.completedDestinations = null;
            sets.consistent = false;
        }
        WRITER.writeValue(file, sets);
    }
}
