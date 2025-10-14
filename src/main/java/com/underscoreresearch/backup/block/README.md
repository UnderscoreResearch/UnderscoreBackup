# Block Package

This package handles the management of data blocks for storage and retrieval in the backup system. It provides the core functionality for breaking files into blocks, managing block assignments, and handling the upload and download of blocks.

## Core Files

- **BlockDownloader.java**: Interface for downloading blocks from storage destinations.
- **BlockFormatFactory.java**: Factory for creating block format plugins.
- **BlockFormatPlugin.java**: Interface for plugins that handle different block formats.
- **FileBlockAssignment.java**: Interface for assigning file data to blocks.
- **FileBlockExtractor.java**: Interface for extracting file data from blocks.
- **FileBlockUploader.java**: Interface for uploading file blocks to storage destinations.
- **FileDownloader.java**: Interface for downloading files from blocks.

## Subpackages

- **assignments**: Contains implementations for different block assignment strategies.
- **implementation**: Contains concrete implementations of the block interfaces.

The block system is a key component of the backup solution's efficiency, enabling features like deduplication and efficient handling of large files with small changes.
