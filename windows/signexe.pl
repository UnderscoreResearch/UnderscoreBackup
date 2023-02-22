use MIME::Base64;

if (!defined($ENV{CERT_DATA}) || !defined($ENV{CERT_PASSWORD})) {
  if ($ARGV[0] eq "optional") {
      print "Skipping signing because CERT_DATA and CERT_PASSWORD environment variables are not set\n";
      exit 0;
  } else {
      die "CERT_DATA and CERT_PASSWORD environment variables must be set";
  }
}

my $CERT_FILE = "build\\cert.pfx";
my $CERT_PASS = $ENV{CERT_PASSWORD};
my $SIGN_TOOL_BASE = "C:\\Program Files (x86)\\Windows Kits\\10\\bin";

open(CERT_FILE, ">$CERT_FILE") or die "Can't open $CERT_FILE: $!";
binmode CERT_FILE;
print CERT_FILE decode_base64($ENV{CERT_DATA});
close CERT_FILE;

sub checkDir {
  my $path = shift;
  my $dir = "$SIGN_TOOL_BASE\\$path\\x64\\signtool.exe";
  return -f $dir;
}

opendir(DIR, $SIGN_TOOL_BASE) or die "Can't open $SIGN_TOOL_BASE: $!";
my @signtools = grep { &checkDir($_)} readdir(DIR);
closedir(DIR);

die "Can't find signtool.exe" unless @signtools;

my $signtool = "$SIGN_TOOL_BASE\\$signtools[$#signtools]\\x64\\signtool.exe";

opendir(DIR, $ARGV[1]) or die "Can't open $ARGV[1]: $!";
for my $file (grep { /\.exe$/ } readdir(DIR)) {
  my $cmd = "\"$signtool\" sign /f \"$CERT_FILE\" /p $CERT_PASS /v /fd sha256 /tr http://timestamp.digicert.com /td sha256 \"$ARGV[1]\\$file\"";
  print "$cmd\n";
  if (system($cmd) != 0) {
    die "Failed to sign $file";
  }
}
closedir(DIR);