#!/bin/sh

rm -rf build/installer
mkdir -p build/installer
cp -r build/image build/installer/UnderscoreBackup
cp -r "osx/Underscore Backup.app" build/installer/UnderscoreBackup
cp osx/preinstall build/installer
cp osx/com.underscoreresearch.UnderscoreBackup.plist build/installer

if [ "X$GITHUB_TARGET" = "Xmacos-arm" ]
then
    export ARCHITECTURE=arm64
else
    export ARCHITECTURE=`uname -m`
fi
mkdir -p build/distributions
pkgbuild --scripts build/installer --nopayload --identifier com.underscoreresearch.UnderscoreBackup --sign "Developer ID Installer: Underscore Research LLC" build/distributions/underscorebackup-$1-$ARCHITECTURE.pkg
