# Block Assignments Package

This package contains implementations of different strategies for assigning file data to blocks. These strategies optimize storage efficiency for different types of files and use cases.

## Files

- **BaseBlockAssignment.java**: Base implementation for block assignment strategies.
- **EncryptedSmallBlockAssignment.java**: Handles encrypted and compressed small file block assignments.
- **GzipLargeFileBlockAssignment.java**: Assigns large files to blocks with GZIP compression.
- **LargeFileBlockAssignment.java**: Base class for large file block assignment strategies.
- **RawLargeFileBlockAssignment.java**: Assigns large files to blocks without compression.
- **SmallFileBlockAssignment.java**: Base class for small file block assignment strategies.
- **ZipSmallBlockAssignment.java**: Assigns small files to blocks with ZIP compression.

The block assignment system is designed to efficiently handle both small and large files, optimizing storage space through techniques like compression and deduplication. Small files can be packed together into single blocks, while large files are split into multiple blocks to enable efficient handling of changes.
