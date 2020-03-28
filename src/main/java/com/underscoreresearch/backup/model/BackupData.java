package com.underscoreresearch.backup.model;

import java.util.function.Supplier;

public class BackupData {
    private byte[] data;
    private Supplier<byte[]> supplier;

    public BackupData(Supplier<byte[]> supplier) {
        this.supplier = supplier;
    }

    public BackupData(byte[] data) {
        this.data = data;
    }

    public synchronized byte[] getData() {
        if (supplier != null) {
            data = supplier.get();
            supplier = null;
        }
        return data;
    }

    public synchronized void clear() {
        supplier = null;
        data = null;
    }
}
