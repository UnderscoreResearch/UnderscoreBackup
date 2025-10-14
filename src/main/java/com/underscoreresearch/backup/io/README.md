# IO Package

This package contains components for input/output operations with various storage destinations in the Underscore Backup system.

## Core Files

- **ConnectionLimiter.java**: Limits the number of concurrent connections.
- **DownloadScheduler.java**: Interface for scheduling downloads.
- **IOIndex.java**: Interface for indexing IO operations.
- **IOPlugin.java**: Interface for IO plugins.
- **IOProvider.java**: Interface for IO providers.
- **IOProviderFactory.java**: Factory for creating IO provider instances.
- **IOProviderUtil.java**: Utility functions for IO providers.
- **IOUtils.java**: General IO utility functions.
- **RateLimitController.java**: Controls IO rate limiting.
- **UploadScheduler.java**: Interface for scheduling uploads.

## Subpackages

- **implementation**: Contains concrete implementations of the IO interfaces.

The IO package is responsible for managing the transfer of data between the local system and storage destinations. It provides a plugin-based architecture that allows for easy addition of new storage destinations, and includes features like connection limiting and rate control to optimize performance and resource usage.
