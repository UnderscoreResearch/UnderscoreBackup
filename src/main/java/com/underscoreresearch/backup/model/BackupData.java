package com.underscoreresearch.backup.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BackupData {
    private byte[] data;
}
