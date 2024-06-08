#!/usr/bin/perl

use strict;
use Cwd;
use File::Spec;
use File::Find qw(finddepth);
use File::Compare;
use IPC::Open2;
use POSIX qw(strftime);

my $originalRoot = getcwd();
my $parentRoot = shift(@ARGV);

if (!$parentRoot) {
    die;
}

my $FIRST_PASSWORD = "bYisMYVs9Qdw";
my $SECOND_PASSWORD = "KqNK4bFj8ZTc";
$ENV{"UNDERSCORE_SUPPRESS_OPEN"} = "TRUE";

my $underscoreBackup = shift(@ARGV);
my $webuiDir = shift(@ARGV);

if (!$underscoreBackup) {
    die;
}

sub cleanPath {
    my ($dir) = @_;

    print "Cleaning $dir\n";
    chdir($originalRoot);
    if (!-d $dir) {
        mkdir($dir) || die;
    } else {
        chdir($dir);

        finddepth { wanted => \&zapFile, no_chdir => 1 }, $dir;
        if (!-d $dir) {
            mkdir($dir) || die;
        }
    }
}

if (!-d $parentRoot) {
    mkdir($parentRoot) || die;
}

my $root = File::Spec->catdir($parentRoot, "root");

my $appRoot = File::Spec->catdir($root, "app");

sub cleanParentRoot {
    &cleanPath($parentRoot);
    &cleanPath($root);
    &cleanPath($appRoot);
}

my $backupRoot = File::Spec->catdir($root, "backup");
my $backupRoot2 = File::Spec->catdir($root, "backup2");
my $testRoot = File::Spec->catdir($root, "data");
my $answerRoot = File::Spec->catdir($root, "answer");
my $shareRoot = File::Spec->catdir($appRoot, "share");
my $configFile = File::Spec->catdir($appRoot, "config.json");
my $keyFile = File::Spec->catdir($appRoot, "key");
my $logFile = File::Spec->catdir($parentRoot, "output.log");
my $sharedPublicKey;

my @directories = (
    "a",
    "a/b",
    "a/b/c",
    "a/c",
    "a/c/d",
    "a/d",
    "b",
    "c",
    "d/e",
    "d/f",
    "d/a",
    "d/a/b",
    "d/a/b/c",
    "d/a/c",
    "d/a/c/d",
    "d/a/d",
    "e/b",
    "e/c",
    "e/d/e",
    "f/d/f",
    "f/a",
    "f/a/b",
    "f/a/b/c",
    "f/a/c",
    "f/a/c/d",
    "f/a/d",
    "f/b",
    "g/c",
    "g/d/e",
    "g/d/f",
    "h",
    "h/i"
);

sub generateFile {
    my ($location, $curGen, $size) = @_;

    #printf("Creating %s/%s of size %d\n", getcwd(), $location, $size);
    open(FILE, ">$location") || die;

    my $chunk = "";

    # Not having 256 here is intentional
    for (my $i = 0; $i < 255; $i++) {
        $chunk .= chr(($i + $curGen) % 255);
    }

    my $i = 0;

    for (; $i + length($chunk) < $size; $i += length($chunk)) {
        print FILE $chunk;
    }

    for (; $i < $size; $i++) {
        print FILE chr(($i + $curGen) % 256);
    }

    close FILE;
}

sub createGeneration {
    my ($curGen, $curRoot) = @_;
    for (my $i = 0; $i < 25; $i++) {
        if (($i % (8 - $curGen)) == 0) {
            chdir($curRoot);
            for my $dir (split(/\//, $directories[$i])) {
                mkdir($dir);
                chdir($dir)
            }

            generateFile($i + $curGen, $curGen, 2 << $i);
            if ($i < 16) {
                generateFile($i, $curGen, 2 << $i);
            }
        }
    }

    # Need one really large file to test super blocks
    if ($curGen == 7) {
        chdir($curRoot);
        generateFile("large", 0, 1 * 1024 * 1024 * 1024);
    }
}

sub generateData {
    my ($generation, $incremental, $curRoot) = @_;

    if (!$curRoot) {
        $curRoot = $testRoot;
    }

    if (!$incremental) {
        &createGeneration($generation, $curRoot);
    }
    else {
        for (my $i = 1; $i <= $generation; $i++) {
            &createGeneration($i, $curRoot);
        }
    }
}

sub zapFile {
    if (!-l && -d _) {
        rmdir($File::Find::name);
    }
    else {
        unlink($File::Find::name);
    }
}

sub prepareTestPath {
    chdir($root);
    finddepth { wanted => \&zapFile, no_chdir => 1 }, $testRoot;
    finddepth { wanted => \&zapFile, no_chdir => 1 }, $answerRoot;
    if (!-d $testRoot) {
        mkdir($testRoot) || die;
    }
    if (!-d $answerRoot) {
        mkdir($answerRoot) || die;
    }
}

sub findConfigLocation() {
    my $config;
    if (-f $logFile) {
        open(LOG, "<$logFile");
        while (<LOG>) {
            if (/URL for configuration\: (\S+)/) {
                $config = $1;
            }
        }
        close LOG;
    }
    return $config;
}

sub executeUnderscoreBackupParameters {
    return (
        $underscoreBackup,
        "-k", $keyFile,
        "-c", $configFile,
        "-m", $appRoot,
        "--log-file", $logFile,
        "-f", "-R", "-d"
    )
};


sub executeUnderscoreBackupWithOutput {
    my @args = &executeUnderscoreBackupParameters();
    push(@args, @_);

    my $cmd = "\"" . join("\" \"", @args) . "\"";
    print "Executing $cmd\n";
    return `$cmd`;
}

sub executeUnderscoreBackupStdinNoCheck {
    my $input = shift(@_);
    chdir($root);
    my @args = &executeUnderscoreBackupParameters();
    push(@args, @_);
    print "Executing " . join(" ", @args) . "\n";

    if ($input) {
        my $pid = open2('>&STDOUT', my $chld_in, @args);

        for my $line (split(/\n/, $input)) {
            syswrite($chld_in, $line . "\n");
            sleep(5);
        }
        waitpid($pid, 0);
        if ($? != 0) {
            die "Failed executing: $?";
        }
        close $chld_in;
    }
    elsif (system(@args) != 0) {
        die "Failed executing ".join(" ", @args).": $?";
    }
}

sub executeUnderscoreBackupStdin {
    &executeUnderscoreBackupStdinNoCheck(@_);

    &validateLog(1);
}

sub executeUnderscoreBackup {
    my @args = ('');
    push(@args, @_);
    &executeUnderscoreBackupStdin(@args);
}

sub createConfigFile {
    my ($retention, $extraDestination, $manifestDestination) = @_;

    if (!$retention) {
        $retention = <<"__EOF__";
      "retention": {
        "defaultFrequency": {
          "duration": 10,
          "unit": "SECONDS"
        },
        "retainDeleted": {
          "unit": "FOREVER"
        }
      }
__EOF__
    }
    my $escapedTestRoot = $testRoot;
    $escapedTestRoot =~ s/\\/\\\\/g;
    my $escapedRoot = $appRoot;
    $escapedRoot =~ s/\\/\\\\/g;
    my $escapedBackupRoot = $backupRoot;
    $escapedBackupRoot =~ s/\\/\\\\/g;
    my $escapedBackupRoot2 = $backupRoot2;
    $escapedBackupRoot2 =~ s/\\/\\\\/g;
    my $escapedShareRoot = $shareRoot;
    $escapedShareRoot =~ s/\\/\\\\/g;
    my $shareDefinition = "";

    if ($sharedPublicKey) {
        $shareDefinition = <<"__EOF__";
  "shares": {
    "$sharedPublicKey": {
      "name": "share",
      "destination": {
          "type": "FILE",
          "encryption": "AES256",
          "endpointUri": "$escapedShareRoot"
      },
      "contents": {
        "roots": [
          {
            "path": "$escapedTestRoot",
            "filters": [
              {
                "type": "EXCLUDE",
                "paths": [ "a" ]
              }
            ]
          }
        ]
      }
    }
  },
__EOF__
    }

    if (!$manifestDestination) {
        $manifestDestination = "\"destination\": \"d0\",\n\"additionalDestinations\": [ \"d1\" ],\n";
    }

    my $config = <<"__EOF__";
{
  "sets": [
    {
      "id": "home",
      "roots": [
        {
          "path": "$escapedTestRoot"
        }
      ],
      "destinations": [
        "d0",
        "d1"
      ],
$retention
    }
  ],
  "destinations": {
    "d0": {
      "type": "FILE",
      "encryption": "PQC",
      "endpointUri": "$escapedBackupRoot"
    },
    "d1": {
      "type": "FILE",
      "encryption": "NONE",
      "endpointUri": "$escapedBackupRoot2"
$extraDestination
    }
  },
  "additionalSources": {
    "same": {
      "type": "FILE",
      "encryption": "PQC",
      "endpointUri": "$escapedBackupRoot"
    },
    "shared": {
      "type": "FILE",
      "encryption": "AES256",
      "endpointUri": "$escapedShareRoot"
    }
  },
$shareDefinition
  "manifest": {
    $manifestDestination
    "pauseOnBattery": false,
    "hideNotifications": true
  }
}
__EOF__

    open(CONFIG, ">$configFile") || die;
    print CONFIG $config;
    close CONFIG;
}

sub cleanRunPath {
    &cleanPath($root);
    &cleanPath($appRoot);
}

sub prepareRunPath {
    &cleanRunPath();
    &createConfigFile(@_);
    &executeUnderscoreBackup("generate-key", "--password", $FIRST_PASSWORD);
}

sub killInteractive {
    &executeUnderscoreBackupStdinNoCheck("", "shutdown");
}

sub validateLog() {
    if (-f $logFile) {
        open(LOG, "<$logFile");
        while (<LOG>) {
            if (/ ERROR /) {
                if (!$_[0]) {
                    &killInteractive();
                }
                die $_;
            }
        }
        close LOG;
    }
}

sub validateAnswer {
    my ($dir1, $dir2) = @_;

    if (!$dir1) {
        $dir1 = $testRoot;
    }
    if (!$dir2) {
        $dir2 = $answerRoot;
    }

    opendir(D, $dir1) || die;
    my @dir1 = readdir(D);
    closedir(D);

    opendir(D, $dir2) || die;
    my @dir2 = readdir(D);
    closedir(D);

    @dir1 = sort @dir1;
    @dir2 = sort @dir2;

    if (scalar(@dir1) != scalar(@dir2)) {
        &killInteractive();
        die "Different contents in $dir1 and $dir2";
    }
    for (my $i = 0; $i < scalar(@dir1); $i++) {
        my $file = $dir1[$i];
        if ($file !~ /^\./) {
            my $file1 = File::Spec->catdir($dir1, $file);
            my $file2 = File::Spec->catdir($dir2, $file);

            if ($file ne $dir2[$i]) {
                &killInteractive();
                die "File $file1 not the same in both directories";
            }

            if (-d $file1 != -d $file2) {
                &killInteractive();
                die "Different kind of file $file1 and $file2";
            }

            if (-f $file1) {
                if (compare($file1, $file2) != 0) {
                    &killInteractive();
                    die "$file1 and $file2 has different contents";
                }
            }
            else {
                &validateAnswer($file1, $file2)
            }
        }
    }
}

sub executeCypressTest {
    chdir($webuiDir);
    my $script = shift(@_);
    my @args = (
        "npx",
        "cypress",
        "run",
        "--headless",
        "--spec", File::Spec->catdir("cypress", File::Spec->catdir("e2e", $script . ".cy.ts"))
    );
    if ($ENV{"CYPRESS_RECORD_KEY"}) {
        if ($ENV{"GITHUB_TARGET"}) {
            push(@args, "--tag", $ENV{"GITHUB_TARGET"} . ",$script");
        }
        push(@args, "--record");
    }
    push(@args, @_);

    my $configLocation = &findConfigLocation();
    if (!$configLocation) {
        print "Could not find config location\n";
        return undef;
    }
    print "Config location: $configLocation\n";

    $ENV{"CYPRESS_TEST_ROOT"} = $appRoot;
    $ENV{"CYPRESS_CONFIG_INTERFACE"} = $configLocation;
    $ENV{"CYPRESS_TEST_DATA"} = $testRoot;
    $ENV{"CYPRESS_TEST_BACKUP"} = $backupRoot;
    $ENV{"CYPRESS_TEST_SHARE"} = $shareRoot;

    if (undef) {
         @args = (
             "npx",
             "cypress",
             "open"
            );
    }

    print "Executing " . join(" ", @args) . "\n";

    if (system(@args) != 0) {
        return undef;
    }
    return 1;
}

my $DELAY = 11;
my $MAX_RETRY = 3;
my @completionTimestamp;
my $pid;

print "Interactive & superblock test\n";

for (my $retry = 1; 1; $retry++) {
    &cleanParentRoot();
    &prepareTestPath();
    &generateData(7, 1);

    $pid = fork();
    if (!$pid) {
        &executeUnderscoreBackup("interactive");
        print "Interactive process terminated\n";
        exit(0);
    }

    sleep(7);

    if (!&executeCypressTest("backup")) {
        &killInteractive();
        if ($retry == $MAX_RETRY) {
            die "Failed to execute Cypress test";
        }
        print "Failed Cypress test retrying for the $retry time\n";
        next;
    }

    &killInteractive();

    waitpid($pid, 0);

    print "Rebuild from backup test\n";

    &prepareTestPath();
    &generateData(7, 1, $answerRoot);
    chdir($root);
    finddepth { wanted => \&zapFile, no_chdir => 1 }, "db";
    unlink($configFile);
    unlink($keyFile);
    $pid = fork();
    if (!$pid) {
        &executeUnderscoreBackup("interactive");
        print "Interactive process terminated\n";
        exit(0);
    }

    sleep(7);

    if (!&executeCypressTest("restore")) {
        &killInteractive();
        if ($retry == $MAX_RETRY) {
            die "Failed to execute Cypress test";
        }
        print "Failed Cypress test retrying for the $retry time\n";
        next;
    }

    &validateAnswer();
    &validateLog();

    &prepareTestPath();
    &generateData(7, 1, $answerRoot);
    chdir($root);

    print "Rebuild from other source\n";

    if (!&executeCypressTest("sourcerestore")) {
        &killInteractive();
        if ($retry == $MAX_RETRY) {
            die "Failed to execute Cypress test";
        }
        print "Failed Cypress test retrying for the $retry time\n";
        next;
    }

    &validateAnswer();
    &validateLog();

    print "Activate share and restore\n";

    &prepareTestPath();
    &generateData(7, 1, $answerRoot);
    chdir($root);

    if (!&executeCypressTest("sharerestore")) {
        &killInteractive();
        if ($retry == $MAX_RETRY) {
            die "Failed to execute Cypress test";
        }
        print "Failed Cypress test retrying for the $retry time\n";
        next;
    }

    &validateAnswer();
    &validateLog();
    last;
}

&killInteractive();
waitpid($pid, 0);

&prepareRunPath();

for my $row (split(/\n/, &executeUnderscoreBackupWithOutput("generate-key", "--additional", "--password", $FIRST_PASSWORD))) {
    print "$row\n";
    if ($row =~ /Generated new key with ID\: (\S+)/) {
        $sharedPublicKey = $1;
    }
}

if (!$sharedPublicKey) {
    die "Didn't find a public key";
}
print "Created public key for sharing $sharedPublicKey\n";

&createConfigFile();

print "Generation 1\n";
&prepareTestPath();
&generateData(1);
&executeUnderscoreBackup("backup");
push(@completionTimestamp, strftime("%Y-%m-%d %H:%M:%S", localtime(time() + 1)));

&executeUnderscoreBackup("activate-shares", "--password", $FIRST_PASSWORD);

sleep($DELAY);
print "Generation 2\n";
&prepareTestPath();
&generateData(2);
&executeUnderscoreBackup("backup");
push(@completionTimestamp, strftime("%Y-%m-%d %H:%M:%S", localtime(time() + 1)));
sleep($DELAY);

&executeUnderscoreBackup("repair-repository", "--password", $FIRST_PASSWORD);

print "Generation 3\n";
&prepareTestPath();
&generateData(3);
&executeUnderscoreBackup("backup");
push(@completionTimestamp, strftime("%Y-%m-%d %H:%M:%S", localtime(time() + 1)));
sleep($DELAY);

&executeUnderscoreBackup("defrag-repository");

print "Generation 4\n";
&prepareTestPath();
&generateData(4);
&executeUnderscoreBackup("backup");
push(@completionTimestamp, strftime("%Y-%m-%d %H:%M:%S", localtime(time() + 1)));
sleep($DELAY);

print "Generation 5\n";
&prepareTestPath();
&generateData(5);
&executeUnderscoreBackup("backup");
push(@completionTimestamp, strftime("%Y-%m-%d %H:%M:%S", localtime(time() + 1)));
sleep($DELAY);

print "Generation 6\n";
&prepareTestPath();
&generateData(6);
&executeUnderscoreBackup("backup");

print "Shared data tests\n";

&prepareTestPath();
&executeUnderscoreBackup("list-keys", "--password", $FIRST_PASSWORD);
&executeUnderscoreBackup("download-config", "--password", $FIRST_PASSWORD, "--source", "shared");
&executeUnderscoreBackup("rebuild-repository", "--password", $FIRST_PASSWORD, "--source", "shared");
&generateData(4, 0, $answerRoot);
finddepth { wanted => \&zapFile, no_chdir => 1 }, File::Spec->catdir($answerRoot, "a");

&executeUnderscoreBackup("restore", "--password", $FIRST_PASSWORD, "-t", $completionTimestamp[3], "--source", "shared", "/");
&validateAnswer();

print "Other source tests\n";

&executeUnderscoreBackup("download-config", "--password", $FIRST_PASSWORD, "--source", "same");
&executeUnderscoreBackup("rebuild-repository", "--password", $FIRST_PASSWORD, "--source", "same");
&executeUnderscoreBackup("rebuild-repository", "--password", $FIRST_PASSWORD, "--source", "same");
&executeUnderscoreBackup("repair-repository", "--password", $FIRST_PASSWORD, "--source", "same");
&executeUnderscoreBackup("defrag-repository", "--source", "same");
&executeUnderscoreBackup("restore", "/", "=", "--password", $FIRST_PASSWORD);
&executeUnderscoreBackup("ls", "/", "--full-path", "--source", "same");
&executeUnderscoreBackup("history", "--source", "same", File::Spec->catdir(File::Spec->catdir($testRoot, "a"), "0"));
&executeUnderscoreBackup("search", "--source", "same", "a");
&prepareTestPath();
&generateData(1, 0, $answerRoot);
&executeUnderscoreBackup("restore", "--password", $FIRST_PASSWORD, "-t", $completionTimestamp[0], "--source", "same", $testRoot, $testRoot);
&validateAnswer();

print "Random bits and ends\n";

&executeUnderscoreBackup("version");
&executeUnderscoreBackup("list-destination");
&executeUnderscoreBackup("list-encryption");
&executeUnderscoreBackup("list-error-correction");

&executeUnderscoreBackup("history", File::Spec->catdir(File::Spec->catdir($testRoot, "a"), "0"));
&executeUnderscoreBackup("download-config", "--password", $FIRST_PASSWORD);
&executeUnderscoreBackup("validate-blocks");
&executeUnderscoreBackup("backfill-metadata", "--password", $FIRST_PASSWORD);

&executeUnderscoreBackup("ls", "/");
&executeUnderscoreBackup("ls", "/", "--full-path");
&executeUnderscoreBackup("search", "a");

&executeUnderscoreBackup("optimize-log");
&executeUnderscoreBackup("restore", "/", "=", "--password", $FIRST_PASSWORD);
&executeUnderscoreBackup("rebuild-repository", "--password", $FIRST_PASSWORD);
&executeUnderscoreBackup("restore", "/", "=", "--password", $FIRST_PASSWORD);
&executeUnderscoreBackup("repository-info");

&prepareTestPath();
&generateData(1, 0, $answerRoot);
&executeUnderscoreBackup("restore", "--password", $FIRST_PASSWORD, "-t", $completionTimestamp[0], $testRoot, $testRoot);
&validateAnswer();

&prepareTestPath();
&generateData(2, 0, $answerRoot);
&executeUnderscoreBackup("restore", "--password", $FIRST_PASSWORD, "-t", $completionTimestamp[1], $testRoot, $testRoot);
&validateAnswer();

&prepareTestPath();
&generateData(3, 0, $answerRoot);
&executeUnderscoreBackup("restore", "--password", $FIRST_PASSWORD, "-t", $completionTimestamp[2], $testRoot, $testRoot);
&validateAnswer();

&prepareTestPath();
&generateData(4, 0, $answerRoot);
&executeUnderscoreBackup("restore", "--password", $FIRST_PASSWORD, "-t", $completionTimestamp[3], $testRoot, $testRoot);
&validateAnswer();

&prepareTestPath();
&generateData(5, 0, $answerRoot);
&executeUnderscoreBackup("restore", "--password", $FIRST_PASSWORD, "-t", $completionTimestamp[4], $testRoot, $testRoot);
&validateAnswer();

&prepareTestPath();
&generateData(6, 0, $answerRoot);
&executeUnderscoreBackup("restore", "--password", $FIRST_PASSWORD, $testRoot, $testRoot);
&validateAnswer();

print "Test repository trimming\n";

&createConfigFile(<<"__EOF__");
      "retention": {
      }
__EOF__

&executeUnderscoreBackup("trim-repository");
&prepareTestPath();
&executeUnderscoreBackup("backup");

&executeUnderscoreBackup("ls", "/");

&executeUnderscoreBackup("restore", "--password", $FIRST_PASSWORD, $testRoot, $testRoot);
&validateAnswer();

print "Test incremental updating\n";

undef @completionTimestamp;
&prepareRunPath('', ",\"maxRetention\": {\"unit\": \"SECONDS\", \"duration\": 30}, \"errorCorrection\": \"RS\"");
&prepareTestPath();

print "Generation 1 incremental\n";
&generateData(1);
&executeUnderscoreBackup("backup");
push(@completionTimestamp, strftime("%Y-%m-%d %H:%M:%S", localtime(time() + 1)));
sleep($DELAY);
print "Generation 2 incremental\n";
&generateData(2);
&executeUnderscoreBackup("backup");
push(@completionTimestamp, strftime("%Y-%m-%d %H:%M:%S", localtime(time() + 1)));
sleep($DELAY);
print "Generation 3 incremental\n";
&generateData(3);
&executeUnderscoreBackup("backup");
push(@completionTimestamp, strftime("%Y-%m-%d %H:%M:%S", localtime(time() + 1)));
sleep($DELAY);
print "Generation 4 incremental\n";
&generateData(4);
&executeUnderscoreBackup("backup");
push(@completionTimestamp, strftime("%Y-%m-%d %H:%M:%S", localtime(time() + 1)));
sleep($DELAY);
print "Generation 5 incremental\n";
&generateData(5);
&executeUnderscoreBackup("backup");
push(@completionTimestamp, strftime("%Y-%m-%d %H:%M:%S", localtime(time() + 1)));
sleep($DELAY);

print "Generation 6 incremental\n";
&generateData(6);
&executeUnderscoreBackup("backup");

&prepareTestPath();
&generateData(1, 1);
&executeUnderscoreBackup("restore", "--password", $FIRST_PASSWORD, "-t", $completionTimestamp[0], "/", "=");
&prepareTestPath();
&generateData(2, 1);
&executeUnderscoreBackup("restore", "--password", $FIRST_PASSWORD, "-t", $completionTimestamp[1], "/", "=");
&prepareTestPath();
&generateData(3, 1);
&executeUnderscoreBackup("restore", "--password", $FIRST_PASSWORD, "-t", $completionTimestamp[2], "/", "=");
&prepareTestPath();
&generateData(4, 1);
&executeUnderscoreBackup("restore", "--password", $FIRST_PASSWORD, "-t", $completionTimestamp[3], "/", "=");
&prepareTestPath();

&generateData(5, 1);
&executeUnderscoreBackup("restore", "--password", $FIRST_PASSWORD, "-t", $completionTimestamp[4], "/", "=");

print "Test changing password\n";

&createConfigFile(undef, undef, "\"destination\": \"d1\",\n");
&executeUnderscoreBackupStdin("$SECOND_PASSWORD\n$SECOND_PASSWORD", "change-password", "--password", $FIRST_PASSWORD);
&cleanPath($backupRoot);
&cleanPath($appRoot);
&createConfigFile(undef, undef, "\"destination\": \"d1\",\n");
&executeUnderscoreBackup("download-config", "--password", $SECOND_PASSWORD);
&executeUnderscoreBackup("rebuild-repository", "--password", $SECOND_PASSWORD);

&prepareTestPath();
&generateData(6, 1);
&executeUnderscoreBackupStdin($SECOND_PASSWORD, "restore", "/", "=");

print "Completed final test execution\n";
exit(0);