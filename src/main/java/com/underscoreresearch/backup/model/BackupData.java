package com.underscoreresearch.backup.model;

import java.util.function.Supplier;

/**
 * Represents data to be backed up.
 * Provides a way to lazily load data using a supplier, or to directly store data.
 */

public class BackupData {
    private byte[] data;
    private Supplier<byte[]> supplier;

    /**
     * Constructor that initializes BackupData with a supplier.
     * The data will be loaded lazily when getData() is called.
     * 
     * @param supplier The supplier that provides the data
     */
    public BackupData(Supplier<byte[]> supplier) {
        this.supplier = supplier;
    }

    /**
     * Constructor that initializes BackupData with data.
     * 
     * @param data The data to store
     */
    public BackupData(byte[] data) {
        this.data = data;
    }

    /**
     * Gets the data, loading it from the supplier if necessary.
     * 
     * @return The data
     */
    public synchronized byte[] getData() {
        if (supplier != null) {
            data = supplier.get();
            supplier = null;
        }
        return data;
    }

    /**
     * Clears the data and supplier.
     */
    public synchronized void clear() {
        supplier = null;
        data = null;
    }
}
