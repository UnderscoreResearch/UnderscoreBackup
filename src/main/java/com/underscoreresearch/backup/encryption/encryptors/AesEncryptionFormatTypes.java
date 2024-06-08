package com.underscoreresearch.backup.encryption.encryptors;

public class AesEncryptionFormatTypes {
    public static final byte CBC = 0;
    public static final byte NON_PADDED_GCM = 1;
    public static final byte PADDED_GCM = 2;
    public static final byte NON_PADDED_GCM_STABLE = 3;
    public static final byte PADDED_GCM_STABLE = 4;
    public static final byte PADDED_PQC = 5;
    public static final byte NON_PADDED_PQC = 6;
}
