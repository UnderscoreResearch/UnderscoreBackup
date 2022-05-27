#!/usr/bin/perl

use strict;
use Cwd;
use File::Spec;
use File::Find qw(finddepth);
use File::Compare;

my $originalRoot = getcwd();
my $root = shift(@ARGV);

if (!$root) {
    die;
}

my $underscoreBackup = shift(@ARGV);
#$underscoreBackup = File::Spec->catdir(getcwd(), "../build/launch4j/underscorebackup");

if (!$underscoreBackup) {
    die;
}

if (!-d $root) {
    mkdir($root) || die;
}

my $testRoot = File::Spec->catdir($root, "data");
my $answerRoot = File::Spec->catdir($root, "answer");
my $backupRoot = File::Spec->catdir($root, "backup");
my $configFile = File::Spec->catdir($root, "config.json");
my $keyFile = File::Spec->catdir($root, "key");
my $logFile = File::Spec->catdir($root, "output.log");

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
        generateFile("large", 0, 20 * 1024 * 1024 * 1024);
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

sub executeUnderscoreBackup {
    chdir($root);
    my @args = (
        $underscoreBackup,
        "--passphrase", "1234",
        "-k", $keyFile,
        "-c", $configFile,
        "--log-file", $logFile,
        "-f", "-R", "-d"
    );
    push(@args, @_);
    print "Executing " . join(" ", @args) . "\n";

    if (system(@args) != 0) {
        die "Failed executing";
    }

    if (-f $logFile) {
        open(LOG, "<$logFile");
        while (<LOG>) {
            if (/ERROR/) {
                die $_;
            }
        }
        close LOG;
    }
}

sub createConfigFile {
    my ($retention) = @_;

    if (!$retention) {
        $retention = <<"__EOF__";
      "retention": {
        "defaultFrequency": {
          "duration": 1,
          "unit": "MINUTES"
        },
        "retainDeleted": {
          "unit": "FOREVER"
        }
      }
__EOF__
    }
    my $escapedTestRoot = $testRoot;
    $escapedTestRoot =~ s/\\/\\\\/g;
    my $escapedRoot = $root;
    $escapedRoot =~ s/\\/\\\\/g;
    my $escapedBackupRoot = $backupRoot;
    $escapedBackupRoot =~ s/\\/\\\\/g;

    open(CONFIG, ">$configFile") || die;
    print CONFIG <<"__EOF__";
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
        "d0"
      ],
$retention
    }
  ],
  "destinations": {
    "d0": {
      "type": "FILE",
      "encryption": "AES256",
      "errorCorrection": "NONE",
      "endpointUri": "$escapedBackupRoot"
    }
  },
  "manifest": {
    "destination": "d0",
    "localLocation": "$escapedRoot"
  }
}
__EOF__

    close CONFIG;
}

sub prepareRunPath {
    chdir($originalRoot);

    finddepth { wanted => \&zapFile, no_chdir => 1 }, $root;
    if (!-d $root) {
        mkdir($root) || die;
    }

    &createConfigFile();
    &executeUnderscoreBackup("generate-key");
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
        die "Different contents in $dir1 and $dir2";
    }
    for (my $i = 0; $i < scalar(@dir1); $i++) {
        my $file = $dir1[$i];
        if ($file !~ /^\./) {
            my $file1 = File::Spec->catdir($dir1, $file);
            my $file2 = File::Spec->catdir($dir2, $file);

            if ($file ne $dir2[$i]) {
                die "File $file1 not the same in both directories";
            }

            if (-d $file1 != -d $file2) {
                die "Different kind of file $file1 and $file2";
            }

            if (-f $file1) {
                if (compare($file1, $file2) != 0) {
                    die "$file1 and $file2 has different contents";
                }
            }
            else {
                &validateAnswer($file1, $file2)
            }
        }
    }
}

&prepareRunPath();

my $DELAY = 61;

my @completionTimestamp;
&prepareTestPath();
print "Generation 1\n";
&generateData(1);
&executeUnderscoreBackup("backup");
push(@completionTimestamp, time());
sleep($DELAY);
print "Generation 2\n";
&prepareTestPath();
&generateData(2);
&executeUnderscoreBackup("backup");
push(@completionTimestamp, time());
sleep($DELAY);
print "Generation 3\n";
&prepareTestPath();
&generateData(3);
&executeUnderscoreBackup("backup");
push(@completionTimestamp, time());
sleep($DELAY);
print "Generation 4\n";
&prepareTestPath();
&generateData(4);
&executeUnderscoreBackup("backup");
push(@completionTimestamp, time());
sleep($DELAY);
print "Generation 5\n";
&prepareTestPath();
&generateData(5);
&executeUnderscoreBackup("backup");
push(@completionTimestamp, time());
sleep($DELAY);
print "Generation 6\n";
&prepareTestPath();
&generateData(6);
&executeUnderscoreBackup("backup");

&prepareTestPath();
&generateData(1, 0, $answerRoot);
&executeUnderscoreBackup("restore", "-t", (time() - $completionTimestamp[0] - 1) . " seconds ago", $testRoot, $testRoot);
&validateAnswer();

&prepareTestPath();
&generateData(2, 0, $answerRoot);
&executeUnderscoreBackup("restore", "-t", (time() - $completionTimestamp[1] - 1) . " seconds ago", $testRoot, $testRoot);
&validateAnswer();

&prepareTestPath();
&generateData(3, 0, $answerRoot);
&executeUnderscoreBackup("restore", "-t", (time() - $completionTimestamp[2] - 1) . " seconds ago", $testRoot, $testRoot);
&validateAnswer();

&prepareTestPath();
&generateData(4, 0, $answerRoot);
&executeUnderscoreBackup("restore", "-t", (time() - $completionTimestamp[3] - 1) . " seconds ago", $testRoot, $testRoot);
&validateAnswer();

&prepareTestPath();
&generateData(5, 0, $answerRoot);
&executeUnderscoreBackup("restore", "-t", (time() - $completionTimestamp[4] - 1) . " seconds ago", $testRoot, $testRoot);
&validateAnswer();

&prepareTestPath();
&generateData(6, 0, $answerRoot);
&executeUnderscoreBackup("restore", $testRoot, $testRoot);
&validateAnswer();

# random bits and ends

&executeUnderscoreBackup("version");
&executeUnderscoreBackup("list-destination");
&executeUnderscoreBackup("list-encryption");
&executeUnderscoreBackup("list-error-correction");

&executeUnderscoreBackup("history", File::Spec->catdir(File::Spec->catdir($testRoot, "a"), "0"));
&executeUnderscoreBackup("download-config");
&executeUnderscoreBackup("validate-blocks");
&executeUnderscoreBackup("backfill-metadata");

&executeUnderscoreBackup("ls", "/");

&executeUnderscoreBackup("optimize-log");
&executeUnderscoreBackup("restore", "/", "=");
&executeUnderscoreBackup("rebuild-repository");
&executeUnderscoreBackup("restore", "/", "=");
&executeUnderscoreBackup("repository-info");

&createConfigFile(<<"__EOF__");
      "retention": {
      }
__EOF__

&executeUnderscoreBackup("trim-repository");
$testRoot = File::Spec->catdir($root, "other");
&createConfigFile(<<"__EOF__");
      "retention": {
      }
__EOF__

&executeUnderscoreBackup("trim-repository");
&executeUnderscoreBackup("ls", "/");

# test updating

undef @completionTimestamp;
&prepareRunPath();
&prepareTestPath();
print "Generation 1 incremental\n";
&generateData(1);
&executeUnderscoreBackup("backup");
push(@completionTimestamp, time());
sleep($DELAY);
print "Generation 2 incremental\n";
&generateData(2);
&executeUnderscoreBackup("backup");
push(@completionTimestamp, time());
sleep($DELAY);
print "Generation 3 incremental\n";
&generateData(3);
&executeUnderscoreBackup("backup");
push(@completionTimestamp, time());
sleep($DELAY);
print "Generation 4 incremental\n";
&generateData(4);
&executeUnderscoreBackup("backup");
push(@completionTimestamp, time());
sleep($DELAY);
print "Generation 5 incremental\n";
&generateData(5);
&executeUnderscoreBackup("backup");
push(@completionTimestamp, time());
sleep($DELAY);
print "Generation 6 incremental\n";
&generateData(6);
&executeUnderscoreBackup("backup");

&prepareTestPath();
&generateData(1, 1);
&executeUnderscoreBackup("restore", "-t", (time() - $completionTimestamp[0] - 1) . " seconds ago", "/", "=");
&prepareTestPath();
&generateData(2, 1);
&executeUnderscoreBackup("restore", "-t", (time() - $completionTimestamp[1] - 1) . " seconds ago", "/", "=");
&prepareTestPath();
&generateData(3, 1);
&executeUnderscoreBackup("restore", "-t", (time() - $completionTimestamp[2] - 1) . " seconds ago", "/", "=");
&prepareTestPath();
&generateData(4, 1);
&executeUnderscoreBackup("restore", "-t", (time() - $completionTimestamp[3] - 1) . " seconds ago", "/", "=");
&prepareTestPath();
&generateData(5, 1);
&executeUnderscoreBackup("restore", "-t", (time() - $completionTimestamp[4] - 1) . " seconds ago", "/", "=");
&prepareTestPath();
&generateData(6, 1);
&executeUnderscoreBackup("restore", "/", "=");

# test superblocks

print "Superblock test";
&prepareTestPath();
&generateData(7, 1);
&executeUnderscoreBackup("backup");
&executeUnderscoreBackup("restore", "/", "=");

&prepareTestPath();
