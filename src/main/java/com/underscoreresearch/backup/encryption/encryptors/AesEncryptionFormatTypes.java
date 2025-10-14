package com.underscoreresearch.backup.encryption.encryptors;

/**
 * Constants defining the different AES encryption format types.
 * These constants are used to identify the encryption format in the encrypted data.
 */
public class AesEncryptionFormatTypes {
    /**
     * AES CBC mode with PKCS5 padding.
     */
    public static final byte CBC = 0;
    
    /**
     * AES GCM mode without padding.
     */
    public static final byte NON_PADDED_GCM = 1;
    
    /**
     * AES GCM mode with padding.
     */
    public static final byte PADDED_GCM = 2;
    
    /**
     * AES GCM mode without padding, with stable output.
     */
    public static final byte NON_PADDED_GCM_STABLE = 3;
    
    /**
     * AES GCM mode with padding, with stable output.
     */
    public static final byte PADDED_GCM_STABLE = 4;
    
    /**
     * Post-Quantum Cryptography with padding.
     */
    public static final byte PADDED_PQC = 5;
    
    /**
     * Post-Quantum Cryptography without padding.
     */
    public static final byte NON_PADDED_PQC = 6;
}
