# Encryption Package

This package contains the encryption components of the Underscore Backup system, providing secure client-side encryption for backup data.

## Core Files

- **EncryptionIdentity.java**: Represents an encryption identity.
- **Encryptor.java**: Interface for encryption implementations.
- **EncryptorFactory.java**: Factory for creating encryptor instances.
- **EncryptorPlugin.java**: Interface for encryption plugins.
- **Hash.java**: Interface for hash functions.
- **HashSha3.java**: SHA-3 hash implementation.
- **IdentityKeys.java**: Manages encryption identity keys.
- **LegacyEncryptionKey.java**: Support for legacy encryption keys.
- **PublicKey.java**: Represents a public encryption key.
- **PublicKeyMethod.java**: Interface for public key methods.

## Subpackages

- **encryptors**: Contains specific encryption implementations.
  - **kyber**: Post-Quantum Kyber encryption implementation.
  - **x25519**: X25519 elliptic curve encryption implementation.

The encryption system is a critical component of the backup solution, ensuring that data is securely encrypted before it leaves the client system. The package supports multiple encryption methods, including Post-Quantum encryption for future-proof security.
