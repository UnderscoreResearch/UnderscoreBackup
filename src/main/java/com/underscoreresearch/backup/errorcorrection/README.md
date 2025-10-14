# Error Correction Package

This package contains the error correction components of the Underscore Backup system, providing resilience against storage corruption.

## Core Files

- **ErrorCorrector.java**: Interface for error correction implementations.
- **ErrorCorrectorFactory.java**: Factory for creating error corrector instances.
- **ErrorCorrectorPlugin.java**: Interface for error correction plugins.

## Subpackages

- **implementation**: Contains specific error correction implementations.
  - **reedsolomon**: Reed-Solomon error correction implementation.

The error correction system adds redundancy to backup data, allowing for recovery from partial data loss or corruption. This is particularly useful for unreliable storage destinations where data integrity cannot be guaranteed.
