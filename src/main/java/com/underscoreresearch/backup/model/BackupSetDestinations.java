package com.underscoreresearch.backup.model;

import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.Sets;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class BackupSetDestinations {
    private static ObjectWriter WRITER = MAPPER.writerFor(BackupSetDestinations.class);
    private static ObjectReader READER = MAPPER.readerFor(BackupSetDestinations.class);

    private boolean initial;
    private Set<String> minUsedDestinations;
    private Set<String> completedDestinations;
    private boolean consistent;

    private static File backupSetLocationInfo(String manifestLocation, BackupSet backupSet) {
        File file = Paths.get(manifestLocation, "db", "sets",
                backupSet.getId() + ".json").toFile();
        file.getParentFile().mkdirs();
        return file;
    }

    public static boolean needStorageValidation(String manifestLocation, BackupSet backupSet,
                                                boolean initial) throws IOException {
        File file = backupSetLocationInfo(manifestLocation, backupSet);
        Set<String> destinations = Sets.newHashSet(backupSet.getDestinations());
        if (file.exists()) {
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
        } else {
            BackupSetDestinations sets = BackupSetDestinations.builder().consistent(false)
                    .minUsedDestinations(destinations)
                    .initial(initial)
                    .build();
            WRITER.writeValue(file, sets);
            return true;
        }
    }

    public static void completedStorageValidation(String manifestLocation, BackupSet backupSet) throws IOException {
        File file = backupSetLocationInfo(manifestLocation, backupSet);
        BackupSetDestinations sets = READER.readValue(file);
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
