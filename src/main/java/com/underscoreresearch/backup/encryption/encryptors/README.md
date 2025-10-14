# Encryption Encryptors Package

This package contains implementations of various encryption algorithms used in the Underscore Backup system.

## Files

- **AesEncryptionFormatTypes.java**: Defines AES encryption format types.
- **AesEncryptorCbc.java**: AES encryption implementation using CBC mode.
- **AesEncryptorFormat.java**: Base format for AES encryption.
- **AesEncryptorGcm.java**: AES encryption implementation using GCM mode.
- **AesEncryptorGcmStable.java**: Stable version of AES-GCM encryption.
- **AesEncryptorPqc.java**: AES encryption with Post-Quantum key exchange.
- **AesEncryptorPqcStable.java**: Stable version of AES with Post-Quantum key exchange.
- **BaseAesEncryptor.java**: Base class for AES encryption implementations.
- **NoneEncryptor.java**: No-op encryptor for unencrypted backups.
- **PQCEncryptor.java**: Post-Quantum Cryptography encryptor.
- **X25519Encryptor.java**: X25519 elliptic curve encryption.

## Subpackages

- **kyber**: Post-Quantum Kyber encryption implementation.
- **x25519**: X25519 elliptic curve encryption implementation.

These encryptors provide a range of encryption options, from no encryption (for non-sensitive data) to state-of-the-art Post-Quantum encryption for maximum security. The plugin-based architecture allows for easy addition of new encryption methods as they become available.
