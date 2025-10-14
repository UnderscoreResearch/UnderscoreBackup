# Configuration Package

This package contains classes for managing the configuration of the Underscore Backup system, including dependency injection modules and factory classes.

## Files

- **BackupModule.java**: Dependency injection module for backup components.
- **CommandLineModule.java**: Dependency injection module for command-line components.
- **EncryptionModule.java**: Dependency injection module for encryption components.
- **ErrorCorrectionModule.java**: Dependency injection module for error correction components.
- **InstanceFactory.java**: Factory for creating instances of components.
- **PluginFactory.java**: Factory for creating plugin instances.
- **RestoreModule.java**: Dependency injection module for restore components.

These classes provide the configuration infrastructure for the application, managing dependencies between components and enabling the plugin-based architecture that allows for extensibility in areas like encryption, error correction, and storage destinations.
