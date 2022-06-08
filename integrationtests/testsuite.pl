#!/usr/bin/perl

use strict;
use Cwd;
use File::Spec;
use File::Find qw(finddepth);
use File::Compare;
use LWP::UserAgent;
use IPC::Open2;

my $ua = new LWP::UserAgent;

my $originalRoot = getcwd();
my $root = shift(@ARGV);

if (!$root) {
    die;
}

my $underscoreBackup = shift(@ARGV);
my $webuiDir = shift(@ARGV);

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
        if ($ENV{"GITHUB_ACTION"}) {
            generateFile("large", 0, 1 * 1024 * 1024 * 1024);
        } else {
            generateFile("large", 0, 17 * 1024 * 1024 * 1024);
        }
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

sub validateLog() {
    if (-f $logFile) {
        open(LOG, "<$logFile");
        while (<LOG>) {
            if (/ ERROR /) {
                die $_;
            }
        }
        close LOG;
    }
}

sub executeUnderscoreBackupStdin {
    my $input = shift(@_);
    chdir($root);
    my @args = (
        $underscoreBackup,
        "-k", $keyFile,
        "-c", $configFile,
        "--log-file", $logFile,
        "-f", "-R", "-d"
    );
    push(@args, @_);
    print "Executing " . join(" ", @args) . "\n";

    if ($input) {
        my $pid = open2('>&STDOUT', my $chld_in, @args);

        for my $line (split(/\n/, $input)) {
            syswrite($chld_in, $line."\n");
            sleep(5);
        }
        waitpid($pid, 0);
        if ($? != 0) {
            die "Failed executing: $?";
        }
        close $chld_in;
    } elsif (system(@args) != 0) {
        die "Failed executing: $?";
    }


    &validateLog();
}

sub executeUnderscoreBackup {
    my @args = ('');
    push(@args, @_);
    &executeUnderscoreBackupStdin(@args);
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

sub cleanRunPath {
    chdir($originalRoot);

    finddepth { wanted => \&zapFile, no_chdir => 1 }, $root;
    if (!-d $root) {
        mkdir($root) || die;
    }
    chdir($root);
}

sub prepareRunPath {
    &cleanRunPath();
    &createConfigFile();
    &executeUnderscoreBackup("generate-key", "--passphrase", "1234");
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

sub killInteractive {
    $ua->get("http://127.0.0.1:12345/fixed/api/shutdown");
}

sub executeCypressTest {
    chdir($webuiDir);
    my @args = (
        "npx",
        "cypress",
        "run",
        "--spec", File::Spec->catdir("cypress", File::Spec->catdir("integration", shift(@_)))
    );
    push(@args, @_);
    print "Executing " . join(" ", @args) . "\n";

    $ENV{"CYPRESS_TEST_ROOT"} = $root;
    $ENV{"CYPRESS_TEST_DATA"} = $testRoot;
    $ENV{"CYPRESS_TEST_BACKUP"} = $backupRoot;

    if (system(@args) != 0) {
        &killInteractive();
        die "Failed executing";
    }
}

my $DELAY = 61;

my @completionTimestamp;
my $pid;

# Test superblocks & interactive

print "Interactive & superblock test\n";
&cleanRunPath();
&prepareTestPath();
&generateData(7, 1);

$pid = fork();
if (!$pid) {
    &executeUnderscoreBackup("interactive", "--developer-mode");
    print "Interactive process terminated\n";
    exit(0);
}

&executeCypressTest("setup.js");
&executeCypressTest("backup.js");

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
    &executeUnderscoreBackup("interactive", "--developer-mode");
    print "Interactive process terminated\n";
    exit(0);
}

&executeCypressTest("setuprebuild.js");
&executeCypressTest("restore.js");

&killInteractive();
waitpid($pid, 0);

&validateAnswer();
&validateLog();

&prepareRunPath();

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

# Random bits and ends

&executeUnderscoreBackup("version");
&executeUnderscoreBackup("list-destination");
&executeUnderscoreBackup("list-encryption");
&executeUnderscoreBackup("list-error-correction");

&executeUnderscoreBackup("history", File::Spec->catdir(File::Spec->catdir($testRoot, "a"), "0"));
&executeUnderscoreBackup("download-config", "--passphrase", "1234");
&executeUnderscoreBackup("validate-blocks");
&executeUnderscoreBackup("backfill-metadata", "--passphrase", "1234");

&executeUnderscoreBackup("ls", "/");

&executeUnderscoreBackup("optimize-log");
&executeUnderscoreBackup("restore", "/", "=", "--passphrase", "1234");
&executeUnderscoreBackup("rebuild-repository", "--passphrase", "1234");
&executeUnderscoreBackup("restore", "/", "=", "--passphrase", "1234");
&executeUnderscoreBackup("repository-info");

&prepareTestPath();
&generateData(1, 0, $answerRoot);
&executeUnderscoreBackup("restore", "--passphrase", "1234", "-t", (time() - $completionTimestamp[0] - 1) . " seconds ago", $testRoot, $testRoot);
&validateAnswer();

&prepareTestPath();
&generateData(2, 0, $answerRoot);
&executeUnderscoreBackup("restore", "--passphrase", "1234", "-t", (time() - $completionTimestamp[1] - 1) . " seconds ago", $testRoot, $testRoot);
&validateAnswer();

&prepareTestPath();
&generateData(3, 0, $answerRoot);
&executeUnderscoreBackup("restore", "--passphrase", "1234", "-t", (time() - $completionTimestamp[2] - 1) . " seconds ago", $testRoot, $testRoot);
&validateAnswer();

&prepareTestPath();
&generateData(4, 0, $answerRoot);
&executeUnderscoreBackup("restore", "--passphrase", "1234", "-t", (time() - $completionTimestamp[3] - 1) . " seconds ago", $testRoot, $testRoot);
&validateAnswer();

&prepareTestPath();
&generateData(5, 0, $answerRoot);
&executeUnderscoreBackup("restore", "--passphrase", "1234", "-t", (time() - $completionTimestamp[4] - 1) . " seconds ago", $testRoot, $testRoot);
&validateAnswer();

&prepareTestPath();
&generateData(6, 0, $answerRoot);
&executeUnderscoreBackup("restore", "--passphrase", "1234", $testRoot, $testRoot);
&validateAnswer();

# Test repository trimmingg

&createConfigFile(<<"__EOF__");
      "retention": {
      }
__EOF__

&executeUnderscoreBackup("trim-repository");
&prepareTestPath();
&executeUnderscoreBackup("backup");

&executeUnderscoreBackup("ls", "/");

&executeUnderscoreBackup("restore", "--passphrase", "1234", $testRoot, $testRoot);
&validateAnswer();

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
&executeUnderscoreBackup("restore", "--passphrase", "1234", "-t", (time() - $completionTimestamp[0] - 1) . " seconds ago", "/", "=");
&prepareTestPath();
&generateData(2, 1);
&executeUnderscoreBackup("restore", "--passphrase", "1234", "-t", (time() - $completionTimestamp[1] - 1) . " seconds ago", "/", "=");
&prepareTestPath();
&generateData(3, 1);
&executeUnderscoreBackup("restore", "--passphrase", "1234", "-t", (time() - $completionTimestamp[2] - 1) . " seconds ago", "/", "=");
&prepareTestPath();
&generateData(4, 1);
&executeUnderscoreBackup("restore", "--passphrase", "1234", "-t", (time() - $completionTimestamp[3] - 1) . " seconds ago", "/", "=");
&prepareTestPath();
&generateData(5, 1);
&executeUnderscoreBackup("restore", "--passphrase", "1234", "-t", (time() - $completionTimestamp[4] - 1) . " seconds ago", "/", "=");

&executeUnderscoreBackupStdin("12345\n12345", "change-passphrase", "--passphrase", "1234");
&prepareTestPath();
&generateData(6, 1);
&executeUnderscoreBackupStdin("12345", "restore", "/", "=");
