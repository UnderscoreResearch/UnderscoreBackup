# File locations.

On Windows the local data files are all located in the user directory `AppData\Local\UnderscoreBackup`.

On Unix or OSX if you run as a non-root user the default location of all files will be `~/.underscoreBackup`.

If running as root the configuration files will be located in `/etc/underscorebackup`, the data files will be in
`/var/cache/underscorebackup` and finally, the log file will be at `/var/log/underscorebackup.log`.

# Normalized paths

Any path in the backup will internally be processed using a normalized format where the path separator is always
represented by a / character. Symbolic links are not followed in the backup and only the real file locations
are stored in the backup.

# Backup configuration file

The backup file contains several sections describing the following things.

* One or more backup sets that describe a group of files to be backed up, how often changes should be scanned, and what
  destinations they should be backed up to. Backup sets will be listed in order of priority. Only a single backup set is
  processed at a time and if a higher priority backup set is scheduled to run before a lower priority one is completed
  the lower priority set will be paused while the higher priority set is processed.

* One or more backup destinations to write backups to.

* A small manifest section containing information on how to treat the backup contents repository.

## Configuration file specification

The configuration file is a JSON file with the following root fields.

### Configuration fields

* **sets** - List of backup sets to process.
* **destinations** - A map of backup destinations and their names.
* **limits** - Global throughput limits.
* **manifest** - Backup manifest options.
* **properties** - Custom additional properties.
* **missingRetention** - If specified used for retention of files not covered by any set. See the set retention
  documentation for more information.
* **additionalSources** - A map of backup destinations for the manifest of secondary backup sources (Not needed if using
  service).

### Backup set definitions

Backup set definitions have the following fields.

* **id** - A unique identifier for the set.
* **root** - The root directory where files from the set reside.
* **exclusions** - A list of regular expressions for files to exclude. The regular expression is applied to the full
  normalized path of any file. This can be used for instance remove specific file extensions from your backup (Don't
  forget the $ character at the end of the expression in this case).
* **destinations** - A list of destination identifiers to which this backup set should be backed up to. If more than one
  is
  listed a copy of the backup will be created on all listed destinations.
* **schedule** - A cron-tab expression that defines how often the backup set should be refreshed. The example above runs
  the backup at 6 AM every day.
* **filters** - A list of filters for the path to either include or exclude. Each entry has the following fields.
    * **paths** - A list of paths that match. These are not regular expressions but must match exactly to the files
      being backed up.
    * **type** - `INCLUDE` or `EXCLUDE` files matching the paths.
    * **children** - Another set of paths under any path matched by this expression. Child paths do not have to have the
      same type as their parent filter.
* **retention** - Define how to keep track of old versions in your backup. By default, if not specified any files not in
  the source, will be deleted once the backup completes.
    * **retainDeleted** - A timespan for how long to keep files in the backup repository after they have been deleted on
      the
      source. If not specified files will be removed immediately.
        * **unit** - Unit of timestamp (FOREVER, IMMEDIATE, SECONDS, MINUTES, HOURS, DAYS, WEEKS, MONTHS, or YEARS).
        * **duration** - How many units for the timespan?
    * **defaultFrequency** - How often by default a new version for a specific file in the set should be retained after
      it has been changed. For instance, if you have a retention of 1 day for a file that updates every 15 minutes only
      one copy of the file will be retained per day at most and the rest will be pruned. If not specified only the most
      recent version of the file will be retained.
        * **unit** - Unit of timestamp (FOREVER, IMMEDIATE, SECONDS, MINUTES, HOURS, DAYS, WEEKS, MONTHS, or YEARS).
        * **duration** - How many units for the timespan?
    * **older** - Different timespans for files as they get older. Contains a list of increasingly older retention
      policies.
        * **validAfter** - The timespan after which this retention policy should override the default retention
          frequency.
            * **unit** - Unit of timestamp (SECONDS, MINUTES, HOURS, DAYS, WEEKS, MONTHS, or YEARS).
            * **duration** - How many units for the timespan?
        * **frequency** - Frequency for how often new copies should be retained once they have reached this age. This
          frequency must be longer than any preceding retention frequencies with shorter validAfter values. If not
          specified then no files older than this will be kept unless they are current.
            * **unit** - Unit of timestamp (FOREVER, IMMEDIATE, SECONDS, MINUTES, HOURS, DAYS, WEEKS, MONTHS, or YEARS).
            * **duration** - How many units for the timespan?

### Backup destination definitions

The destinations item is a map where the key is the unique identifier for the destination. The value is an object with
the following keys.

* **type** - The type of destination. Supported values are `UB`, `FILE`, `SMB`, `DROPBOX`, and `S3`.
* **encryption** - Encryption to use for data stored at this location. Supported values are `NONE`
  and `AES256`. If not specified defaults to `AES256`.
* **errorCorrection** - Error correction to use. Can be either `RS` for Reed Solomon parity or `NONE` for no error
  correction. If not specified defaults to `NONE`.
* **endpointUri** - Endpoint URI. Where the root of this destination is located.
* **principal** - Username or access key for the destination.
* **credential** - Credential for principal.
* **maxRetention** - Refresh any data stored at this destination that is older than this retention. Only applies to
  block
  data, not other backup metadata.
    * **unit** - Unit of timestamp (FOREVER, IMMEDIATE, SECONDS, MINUTES, HOURS, DAYS, WEEKS, MONTHS, or YEARS).
    * **duration** - How many units for the timespan?
* **properties** - Specific properties for destination type. Notably, the `region` for S3 buckets must be specified here
  if not `us-east-1`.
* **limits** - Upload and download limits rate limit for the destination.
    * **maximumUploadBytesPerSecond** - Maximum bytes per second for uploading data to this destination.
    * **maximumDownloadBytesPerSecond** - Maximum bytes per second for downloading data from this destination.

### Backup manifest definition

Contains information of how the backup repository is managed.

* **destination** - ID for destination to use for storing the log and other manifest data of the repository.
* **maximumUnsyncedSize** - Maximum size of the change log to keep locally before uploading to the destination.
* **maximumUnsyncedSeconds** - Maximum time that changes are kept locally before they are forced to be uploaded to
  the backup destination.
* **interactiveBackup** - If set and true then start running a backup whenever you launch in interactive mode.
* **optimizeSchedule** - If set this will be a schedule at which a log optimization operation will be run once a
  scheduled or manual backup completes.
* **trimSchedule** - If set this will be a schedule of how often the trim operation will look for and delete unused
  storage.
* **authenticationRequired** - If set to true require providing encryption password to access UI.
* **scheduleRandomize** - Amount of time to randomly add to any scheduled time to jitter the start of next scheduled
  run.
    * **unit** - Unit of timestamp (SECONDS, MINUTES, HOURS, DAYS, WEEKS, MONTHS, or YEARS).
    * **duration** - How many units for the timespan?
* **pauseOnBattery** - Pause backups when running on battery. Defaults to on if not specified.
* **hideNotifications** - Hide UI notifications except for errors. Defaults to off if not specified.
* **versionCheck** - Controls whether to check for new versions online periodically. Defaults to on.

### Global limits

These are specified in a `limits` object in the root configuration object.

* **maximumUploadBytesPerSecond** - Maximum bytes per second for uploading data to this destination.
* **maximumDownloadBytesPerSecond** - Maximum bytes per second for downloading data from this
* **maximumUploadThreads** - Maximum number of active threads uploading data to a destination.
* **maximumDownloadThreads** - Maximum number of active threads downloading data from a destination.

### Additional properties

There is another root property which is a map of strings to string for additional specialized properties. Here are some
currently existing property keys used.

* **smallFileBlockAssignment.maximumSize** - Maximum size for small block assignment (Suitable for smaller files where
  it is important to pack multiple files into a single block). Defaults to 4091kb.
* **smallFileBlockAssignment.targetSize** - Target size of a small block total block size. Defaults to 8182kb.
* **largeBlockAssignment.raw** - If set to true, don't GZip large blocks.
* **largeBlockAssignment.maximumSize** - Maximum size of large blocks. Defaults to 8182kb.
* **reedSolomon.dataSlices** - Number of Reed Solomon data slices to use. Defaults to 17.
* **reedSolomon.paritySlices** - Number of Reed Solomon parity slices to use. Defaults to 3.
* **noneErrorCorrection.maximumFileSize** - Maximum part size for `none` error correction. Defaults to 16384kb.
* **maximumRefreshedBytes** - Maximum number of bytes to refresh because of destination retention setting per run.
* **crossSourceDedupe** - Allow data to be deduped across multiple sources. Affects how the keys to AES encryption are
  handled.
