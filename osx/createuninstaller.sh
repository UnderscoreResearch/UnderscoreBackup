#!/bin/sh

rm -rf build/uninstaller
mkdir -p build/uninstaller
cp osx/uninstall build/uninstaller/preinstall
chmod a+x build/uninstaller/preinstall

mkdir -p build/distributions
pkgbuild --scripts build/uninstaller --nopayload --identifier com.underscoreresearch.UnderscoreBackup --sign "Developer ID Installer: Underscore Research LLC" build/distributions/underscorebackup-uninstall-$1.pkg

