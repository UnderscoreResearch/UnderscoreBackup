#!/usr/bin/perl

use File::Find qw(finddepth);
use File::Spec;
use Config;
use Cwd;

sub zapFile {
    if (!-l && -d _) {
        rmdir($File::Find::name);
    }
    else {
        unlink($File::Find::name);
    }
}

sub cleanBuild {
    finddepth { wanted => \&zapFile, no_chdir => 1 }, "build";
}

sub buildNpm {
    chdir("webui");
    if (system("npm", "run", "build") != 0) {
        die;
    }
    chdir("..");
}

if ($Config{osname} eq "msys") {
    die "Don't run in Git Bash";
}

sub executeGradle {
    if (system(File::Spec->catdir(getcwd(), "gradlew"), shift(@_)) != 0) {
        die;
    }
}

&cleanBuild();

&buildNpm();

&executeGradle("allDistTest");

print "Successfull\n";
