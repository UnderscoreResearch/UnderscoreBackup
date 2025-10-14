# CLI Commands Package

This package contains implementations of the various commands available in the Underscore Backup command-line interface.

## Files

- **ActivateSharesCommand.java**: Activates shared backup data from other users.
- **BackupCommand.java**: Executes the backup process.
- **ChangePasswordCommand.java**: Changes the encryption password.
- **Command.java**: Base interface for command implementations.
- **CommandPlugin.java**: Interface for command plugins.
- **CompactRepositoryCommand.java**: Compacts the backup repository to optimize storage.
- **ConfigureCommand.java**: Configures the backup system.
- **DownloadConfigCommand.java**: Downloads configuration from a backup destination.
- **GenerateKeyCommand.java**: Generates encryption keys.
- **GuiCommand.java**: Launches the graphical user interface.
- **HistoryCommand.java**: Shows backup history for files.
- **InteractiveCommand.java**: Starts interactive mode.
- **ListDestinationCommand.java**: Lists available backup destinations.
- **ListEncryptionCommand.java**: Lists available encryption methods.
- **ListErrorCorrectionCommand.java**: Lists available error correction methods.
- **ListKeysCommand.java**: Lists encryption keys.
- **LsCommand.java**: Lists files in the backup repository.
- **OptimizeLogCommand.java**: Optimizes the backup log.
- **RebuildRepositoryCommand.java**: Rebuilds the local repository from backup.
- **RepairRepositoryCommand.java**: Repairs repository inconsistencies.
- **RepositoryInfoCommand.java**: Shows information about the repository.
- **RestoreCommand.java**: Restores files from backup.
- **SearchCommand.java**: Searches for files in the backup.
- **ShutdownCommand.java**: Shuts down the backup service.
- **SimpleCommand.java**: Base class for simple commands.
- **TrimRepositoryCommand.java**: Removes unused data from the repository.
- **ValidateBlocksCommand.java**: Validates block integrity.
- **VersionCommand.java**: Shows version information.

These commands provide the full range of functionality needed to manage backups, from configuration and execution to restoration and maintenance.
