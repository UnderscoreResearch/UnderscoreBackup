# Error Correction Implementation Package

This package contains implementations of error correction algorithms for the Underscore Backup system.

## Files

- **NoneErrorCorrector.java**: No-op error corrector for when error correction is not needed.
- **ReedSolomonErrorCorrector.java**: Implementation of Reed-Solomon error correction.

## Subpackages

- **reedsolomon**: Contains the core Reed-Solomon algorithm implementation.

These implementations provide options for error correction, from no error correction (for reliable storage) to Reed-Solomon error correction for unreliable storage destinations. The plugin-based architecture allows for easy addition of new error correction methods as needed.
