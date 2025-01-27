#!/usr/bin/perl

use File::Copy;
use File::Spec;
use Cwd;
use strict;

my $java = shift(@ARGV);
my $extra = shift(@ARGV);

if (!-d "build") {
    mkdir("build") || die;
}

my $pwd = cwd();

my $JAVA_VERSION="21";

if (!-d "build/amazon-corretto-$JAVA_VERSION.jdk") {

    print "Download Amazon Corretto $JAVA_VERSION JDK from $java\n";
    system("curl", "-L", "-o", "build/jdk.tar.gz", $java) && die;
    chdir("build") || die;
    if (!-d "unpack") {
        mkdir("unpack") || die;
    }
    chdir("unpack") || die;
    system("tar", "-xf", "../jdk.tar.gz") && die;
    opendir (DIR, ".") or die $!;
    my @dirs = readdir(DIR);
    closedir(DIR);

    my $moved = 0;
    foreach my $dir (@dirs) {
        print "Found $dir\n";
        if ($dir !~ /^\./) {
            move($dir, "../amazon-corretto-$JAVA_VERSION.jdk") || die;
            if ($moved) {
                die "Found more than one directory in the tarball";
            }
            $moved = 1;
        }
    }

    if (!$moved) {
        die "Did not find any directories in the tarball";
    }
}

chdir($pwd) || die;

my $javaHome = File::Spec->catdir($pwd, "build", "amazon-corretto-$JAVA_VERSION.jdk");
if ($extra) {
    $javaHome = File::Spec->catdir($javaHome, $extra);
}

$ENV{"JAVA_HOME"} = $javaHome;
$ENV{"PATH"} = File::Spec->catdir($javaHome, "bin").":$ENV{PATH}";

print "JAVA_HOME=$ENV{JAVA_HOME}\n";
print "PATH=$ENV{PATH}\n";

system(File::Spec->catdir($pwd, "gradlew"), "allDistTest", "--info") && die;
