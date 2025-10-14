# Underscore Backup Core Package

This is the root package for the Underscore Backup application. It contains all the core functionality organized into the following subpackages:

- **block**: Handles data block management, including block assignments, uploading, and downloading
- **cli**: Command-line interface and web UI integration
- **configuration**: Configuration management and settings
- **encryption**: Encryption mechanisms including Post-Quantum encryption
- **errorcorrection**: Error correction capabilities for unreliable storage
- **file**: File management, change detection, and metadata handling
- **io**: Input/output operations for various storage destinations
- **machinestate**: Handles operating system state across different operating systems
- **manifest**: Repository state tracking and metadata management
- **model**: Data models used throughout the application
- **service**: Integration with the optional Underscore Backup service
- **utils**: Utility functions and helpers

The application is designed with a modular architecture that allows for extensibility through plugins, particularly for encryption algorithms, error correction methods, and storage destinations.
