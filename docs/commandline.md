# Using from the command line

Below are just a few things to get your started using Underscore Backup from the command line.

## Run your backup

Now you can just execute the backup from the command line using the following command.

    > underscorebackup backup

This command will only exit if you have no backup sets with schedules and when a full backup of all of your included
files. You can start the service by using `systemctl start underscorebackup` and stop it with
`systemctl stop underscorebackup`. The logs for the backup will be written to `/var/log/underscorebackup.log` by default.

## Browse your backup

You can check the contents of your repository by issuing the following command.

    > underscorebackup ls

This will show the contents of the backup for the path you are currently on. You can specify a path to list the location
for that path. Use the `-R` flag for listing the contents of subdirectories and the `-h` flag for getting humanly readable
sizes.

## Restoring files from backup

Use the following command to restore files.

    > underscorebackup restore [FILES]... [DESTINATION]

If no files or destination are specified, then all files in the current directory will be restored. Use the `-R` flag to
restore all files in subdirectories.

## Going back in time

If you want to go back in time and restore or look at contents of your backup older than your most recent backup use
the `-t` parameter to specify the time of the repository to browse. The format of the parameter is almost any English
phrase such as "1 hour ago", "Last Thursday at noon" or just a timestamp all work.

## Disaster has struck and my entire filesystem is gone. How do I restore it?

If you prefer a more manual approach you have to reconstruct your configuration file for the backup manifest destination.
From there on you can run the following command which will download your original configuration files and encryption keys.

    > backup download-config

Then run the following command.

    > backup rebuild-repository

Depending on the size of your repository this might take a few minutes. This operation will download and replay all your
backup activity from the destination and recreate the local repository. After this has been completed you use the `ls`
and `restore` commands above as before your filesystem was lost.

## Where do I go from here?

If you run the command line without any parameter it will list all the available commands.

    > underscorebackup

This will tell you all the available commands and the command line options you can use.