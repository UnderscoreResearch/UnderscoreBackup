Serverless backup solution with client side encryption with the following features.

* Public-key based Post Quantum encryption allows continuously running backups that can only be read with a key not
  available on the server running the backup.

* Ties into [a free service](https://underscorebackup.com/) that helps coordination of your backup sources, optional
  encryption key recovery, and coordination of sharing of backup data. However, all online services are optional, and
  you can run the backup software without an online account.

* Provides optional [paid-for storage service](https://underscorebackup.com/pricing) as part of the service offering.
  This is also entirely optional, and you can use any supported storage service you wish.

* Pre-egress encryption means no proprietary data leaves your system in a format where it can be compromised as long as
  your private key is not compromised.

* Allow selective sharing of backup data with other participants with complete cryptographically enforced security.

* Designed from the ground up to manage very large backup sets with multiple TB of data and millions of files in a
  single repository.

* Multi-platform support based on Java 21. Linux, Windows, and MacOS are officially supported but should run anywhere
  Java 21 is supported.

* Low resource requirements, runs efficiently with 256MB of heap memory even if the backup sets have millions of files
  and terabytes of data.

* Efficient storage of both large and small files with built-in deduplication of data.

* Handles backing up large files with small changes in them efficiently.

* Optional error correction to support unreliable storage destinations.

* Encryption, error correction, and destination IO are plugin based and easily extendable.

* Supports Dropbox, local file, Windows Shares, S3, Wasabi, Backblaze B2 and iDrive E2 for backup destinations.

* Fully fledged web-based UI for both initial setup, monitoring and restore operations.

Getting Started
=============

## Download the binary or build it from the source

Download one of the releases and unpack it somewhere where it can be run.
On either Windows or MacOS you can simply run the installer and you will be prompted with
a web page for completing the setup. On these, systems the software will run as a user process
that automatically launches at log-in.

If running on Unix or Linux by default the software will run as a service owned by root.
Once installed can configure it by running the following command as root.

    > underscorebackup configure

It should launch a web page that will guide you through the initial setup process. If you are running from a console
where it is not possible to launch a browser the first log entry should list the URL you need to go to interact with
the software manually.

## Disaster has struck and my entire filesystem is gone. How do I start restoring files?

Start by installing Underscore Backup from scratch. Start by reinstalling the application.

If you are using the service to manage configuration connect to the service and simply choose the old existing source
and adopt it to start rebuilding the local repository. If you are not using the service simply point it to the
destination that you used for your repository metadata. The setup wizard will detect that an installation exists there
and rebuild the local repository. Once the local repository is restored you can start restoring any files you need
from your old backup.

## Using the command line

See [separate doc](docs/commandline.md) for information about how to use the command line.

## Configuration and files

See [separate doc](docs/configuration.md) for information about the configuration files and how to use them.
