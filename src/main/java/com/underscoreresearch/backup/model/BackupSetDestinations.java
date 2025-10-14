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

/**
 * Represents the destinations used by a backup set.
 * Contains information about which destinations are used by a backup set,
 * which ones have been completed, and whether the set is consistent.
 */

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Slf4j
public class BackupSetDestinations {
    /**
     * Writer for serializing BackupSetDestinations objects.
     */
    private static final ObjectWriter WRITER = MAPPER.writerFor(BackupSetDestinations.class);
    
    /**
     * Reader for deserializing BackupSetDestinations objects.
     */
    private static final ObjectReader READER = MAPPER.readerFor(BackupSetDestinations.class);

    /**
     * Flag indicating whether this is the initial backup for the set.
     */
    private boolean initial;
    
    /**
     * Set of destination IDs that must be used by the backup set.
     */
    private Set<String> minUsedDestinations;
    
    /**
     * Set of destination IDs that have been completed.
     */
    private Set<String> completedDestinations;
    
    /**
     * Flag indicating whether the backup set is consistent.
     */
    private boolean consistent;

    /**
     * Gets the file where the backup set destinations information is stored.
     * 
     * @param manifestLocation The location of the manifest
     * @param backupSet The backup set
     * @return The file where the information is stored
     */
    private static File backupSetLocationInfo(String manifestLocation, BackupSet backupSet) {
        File file = Paths.get(manifestLocation, "db", "sets",
                backupSet.getId() + ".json").toFile();
        createDirectory(file.getParentFile(), true);
        return file;
    }

    /**
     * Checks if storage validation is needed for a backup set.
     * 
     * @param manifestLocation The location of the manifest
     * @param backupSet The backup set to check
     * @param initial Whether this is the initial backup for the set
     * @return True if storage validation is needed, false otherwise
     * @throws IOException If there's an error reading or writing the destinations file
     */
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

    /**
     * Marks storage validation as completed for a backup set.
     * 
     * @param manifestLocation The location of the manifest
     * @param backupSet The backup set that was validated
     * @throws IOException If there's an error reading or writing the destinations file
     */
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
