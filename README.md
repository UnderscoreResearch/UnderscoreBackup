**IMPORTANT: This application is not ready for production use!**

The application is still under heavy development and even though most functionality is already functional you should not rely on this application for your primary backup of production data.

A backup solution with the following features.

* Public key based encryption allows continuously running backups that can only be read with a key not available on the server running the backup.

* Pre-egress encryption means no proprietary data leaves your system in a format where it can be compromised as long as your private key is not compromised.

* Runs entirely without a service component.

* Designed from the ground up to manage very large backup sets with multiple TB of data and millions of file in a
single repository.

* Multi-platform support based on Java 8.

* Low resource requirements, runs efficiently with 256MB of heap memory even if the backup sets have millions of files.

* Efficient storage of both large and small file with built-in deduplication of data.

* Handles backing up large files with small changes in them efficiently.

* Optional error correction to support unreliable storage destinations.

* Encryption, error correction and destination IO are plugin based and easily extendable.

* Supports local file, Windows Shares and S3 for backup destinations.

* Fully fledged web based UI for both initial setup, monitoring and restore operations.

Getting Started
=============

## Download the binary or build it from source

Download one of the releases and unpack somewhere where you can run it. On Linux it is recommended to place it in `/opt/underscorebackup`. On Windows the installer will place the files in the `C:|Program Files\UnderscoreBackup\`.

If running on Unix you should decide if you wish to run the software as root to perform a full system backup or as a specific user. Once installed simply launch the command.

    > underscorebackup interactive

It should launch a web page that will guide you through the initial setup process. If you are running from a console where it is not possible to launch a browser the first log entry should list the URL you need to go to to interact with
the software manually. Once the configuration is complete you can also set the it up to run as a service on Linux using the provided `startbackupservice.sh` script.

## File locations.

On Windows the local data files are all located in the user directory `AppData\Local\UnderscoreBackup`.

On Unix or OSX if you run as a non root user the default location of all files will be `~/.underscoreBackup`.

If running as root the configuration files will be located in `/etc/underscorebackup`, the data files will be in
`/var/cache/underscorebackup` and finally the log file will be at `/var/log/underscorebackup.log`.

## Backup configuration file

The backup file contains several sections describing the following things.

* One or more backup sets which describes a group of files to be backed up, how often changes should be scanned and what destinations they should be backed up to. Backup sets will be listed in order of priority. Only a single backup set is processed at a time and if a higher priority backup set is scheduled to run before a lower priority one is completed the lower priority set will be paused while the higher priority set is processed.

* One or more backup destinations which to write backups to.

* A small manifest section containing information of how to treat the backup contents repository.

### Normalized paths
Any path in the backup will internally be processed using a normalized format where the path separator is always represented by a / character. Also files are backed up by following any symbolic links to the real location of files.

## Run your backup

Now you can just execute the backup from the command line using the following command.

    > underscorebackup backup
    
This command will only exit if you have no backup sets with schedules and when a full backup of all of your included files. You can also consider running it as a service on Ubuntu machines by copying the file `underscorebackup.service` to your `/etc/systemd/system` directory. You can then start the backup by using `systemctl start underscorebackup`. This assumes you have unpacked the binaries into `/opt/underscorebackup`. The logs for the backup will be written to `/var/log/underscorebackup.log` by default.

## Browse your backup

You can check the contents of your repository by issuing the following command.

    > underscorebackup ls

This will show the contents of the backup for the path you are currently on. You can specify a path to list the location for that path. Use the `-R` flag for listing contents of subdirectories and the `-h` flag for getting human readable sizes.

## Restoring files from backup

Use the following command to restore files.

    > underscorebackup restore [FILES]... [DESTINATION]
    
If no files or destination are specified then all files in the current directory will be restored. Use the `-R` flag to restore all files in subdirectories.

## Going back through time

If you want to go back in time and restore or look at contents of your backup older than your most recent backup use the `-t` parameter to specify the time of the repository to browse. The format of the parameter is almsot any english phrase such as "1 hour ago", "Last thursday and noon" or just a timestamp all work.

## Disaster has struck and my entire filesystem is gone. How do I restore it?

Start by installing Understore Backup from scratch. Then go to your repository destination and download the config.json and key file from there to your machine. Then run the following command.

    > backup rebuild-repository

 Depending on the size of your repository this might take a few minutes. This operation will download and replay all your backup activity from the destination and recreate the local repository. After this has completed you use the `ls` and `restore` commands above as before your filesystem was lost.

# Configuration file specification

## Backup set definitions
Backup set definitions have the following fields.

* **id** - A unique identifier for the set.
* **root** - The root directory where files from the set reside.
* **exclusions** - A list of regular expressions for files to exclude. The regular expression is applied to the full normalized path of any file. This can be used for instance remove specific file extensions from your backup (Don't forget the $ character at the end of the expression in this case).
* **destinations** - A list of destination identifiers which this backup set should be backed up to. If more than one is listed a cope of the backup will be created on all listed destinations.
* **schedule** - A cron-tab expression that defines how often the backup set should be refreshed. The example above runs the backup at 6AM every day.
* **filters** - A list of filters for the path to either include or exclude. Each entry has the following fields.
  * **paths** - A list of paths that match. These are not regular expressions but must match exactly to the files being backed up.
  * **type** - `INCLUDE` or `EXCLUDE` files matching the paths.
  * **children** - Another set of paths under any path matched by this expression. Child paths do not have to have the same type as their parent filter.
* **retention** - Define how to keep track of old versions in your backup. By default if not specified any files not in the source will be deleted once the backup completes.
  * **retainDeleted** - A timespan for how long to keep files in backup repository after they have been deleted on the source. If not specified files will be removed immediately.
    * **unit** - Unit of timestamp (SECONDS, MINUTES, HOURS, DAYS, WEEKS, MONTHS or YEARS).
    * **duration** - How many of units for the timespan.
  * **defaultFrequency** - How often by default a new version for a specific file in the set should be retained after it has been changed. For instance if you have a retention of 1 day for a file that updates every 15 minutes only one copy of the file will be retained per day at most and the rest will be pruned. If not specified only the most recent version of the file will be retained.
    * **unit** - Unit of timestamp (SECONDS, MINUTES, HOURS, DAYS, WEEKS, MONTHS or YEARS).
    * **duration** - How many of units for the timespan.
  * **older** - Different timespans for files as they get older. Contains a list of increasingly older retention policies.
    * **validAfter** - The timespan after which this retention policy should override the default retention frequency.
      * **unit** - Unit of timestamp (SECONDS, MINUTES, HOURS, DAYS, WEEKS, MONTHS or YEARS).
      * **duration** - How many of units for the timespan.
    * **frequency** - Frequency for how often new copies should be retained once they have reached this age. This frequence must be longer than any preceeding retention frequencies with shorter validAfter values. If not specified then no files older than this will be kept unless they are current.
      * **unit** - Unit of timestamp (SECONDS, MINUTES, HOURS, DAYS, WEEKS, MONTHS or YEARS).
      * **duration** - How many of units for the timespan.

## Backup destination definitions
The destinations item is a map where the key is the unique identifier for the destination. The value is an object with the following keys.
* **type** - The type of destination. Currently `FILE` and `S3` are the supported values.
* **encryption** - Encryption to use for data stored at this location. Currently supported values are `NONE` and `AES256`. If not specified defaults to `AES256`.
* **errorCorrection** - Error correction to use. Can be either `RS` for Reed Solomon parity or `NONE` for no error correction. If not specified defaults to `NONE`.
* **endpointUri** - Endpoint URI. Where the root of this destination is located.
* **principal** - Username or access key for destination.
* **credential** - Credential for principal.
* **properties** - Specific properties for destination type. Notably the `region` for S3 buckets must be specified here if not us-east-1.
* **limits** - Upload and download limits rate limits for destination.
    * **maximumUploadBytesPerSecond** - Maximum bytes per second for uploading data to this destination.
    * **maximumDownloadBytesPerSecond** - Maximum bytes per second for downloading data from this destination.
    
## Backup manifest definition
Contains information of how the backup repository is managed.

* **destination** - What destination to use for storing the log of the repository.
* **localLocation** - Where the local location of the repository cache should be located. If not specified defaults to `C:\UndersoreBackup\` on Windows and `/var/cache/underscorebackup` everywhere else.
* **maximumUnsyncedSize** - Maximum size of the change log to keep locally before uploading to destination.
* **maximumUnsyncedSeconds** - Maximum time that changes are kept locally before they are forced to be uploaded to backup destination.
* **interactiveBackup** - If set and true then start running a backup whenever you launch in interactive mode.
* **configUsername** - Username required to log into the config web interface.
* **configPassword** - Password required to log into the config web interface.

## Global limits
These are specified in a `limits` object in the root configuration object.

* **maximumUploadBytesPerSecond** - Maximum bytes per second for uploading data to this destination.
* **maximumDownloadBytesPerSecond** - Maximum bytes per second for downloading data from this 
* **maximumUploadThreads** - Maximum number of active threads uploading data to a destination.
* **maximumDownloadThreads** - Maximum number of active threads downloading data from a destination.

## Additional properties
There is another root property which is a map of strings to string for additional specialized properties. Here are some currently existing property keys used.

* **smallFileBlockAssignment.maximumSize** - Maximum size for small block assignment (Suitable for smaller files where it is important to pack multiple files into a single block). Defaults to 4091kb.
* **smallFileBlockAssignment.targetSize** - Target size of a small block total block size. Defaults to 8182kb.
* **largeBlockAssignment.raw** - If set to true, don't GZip large blocks.
* **largeBlockAssignment.maximumSize** - Maximum size of large blocks. Defaults to 8182kb.
* **reedSolomon.dataSlices** - Number of Reed Solomon data slizes to use. Defaults to 17.
* **reedSolomon.paritySlices** - Number of Reed Solomon parity slizes to use. Defaults to 3.
* **noneErrorCorrection.maximumFileSize** - Maximum part size for none error correction. Defaults to 16384kb.

