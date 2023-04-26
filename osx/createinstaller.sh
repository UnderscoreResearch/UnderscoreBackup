#!/bin/sh

rm -rf build/installer
mkdir -p build/installer
cp -r build/image build/installer/UnderscoreBackup
cp -r "osx/Underscore Backup.app" build/installer/UnderscoreBackup
cp osx/preinstall build/installer
chmod a+x build/installer/preinstall
cp osx/com.underscoreresearch.UnderscoreBackup.plist build/installer

export ARCHITECTURE=`uname -m`

mkdir -p build/distributions
pkgbuild --scripts build/installer --nopayload --identifier com.underscoreresearch.UnderscoreBackup --sign "Developer ID Installer: Underscore Research LLC" build/distributions/underscorebackup-$1-$ARCHITECTURE.pkg
