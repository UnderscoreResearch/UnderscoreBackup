#!/bin/sh
set -e
set -x

./gradlew jpackageImage -Pinstaller_type=rpm
rm -rf build/installerimage
mkdir -p build/distributions
mkdir -p build/installerimage/opt/underscorebackup
mkdir -p build/installerimage/etc/systemd/system
mkdir -p build/installerimage/underscorebackup-$VERSION
mkdir -p build/installerimage/usr/share/applications
mkdir -p build/installerimage/usr/share/icons/hicolor/scalable/apps
mkdir -p build/rpm/SOURCES
mkdir -p build/rpm/SPECS
mv build/jpackage/underscorebackup/bin build/installerimage/opt/underscorebackup
mv build/jpackage/underscorebackup/lib build/installerimage/opt/underscorebackup
cp README.md build/installerimage/opt/underscorebackup
cp scripts/underscorebackup.service build/installerimage/etc/systemd/system/underscorebackup.service
cp linux/underscorebackup.desktop build/installerimage/usr/share/applications/
cp linux/underscorebackup.svg build/installerimage/usr/share/icons/hicolor/scalable/apps/
mv build/installerimage/etc build/installerimage/opt build/installerimage/usr build/installerimage/underscorebackup-$VERSION

( cd build/installerimage ; tar czf ../rpm/SOURCES/underscorebackup.tar.gz . )
sed s/VERSION/$VERSION/ < linux/underscorebackup.spec > build/rpm/SPECS/underscorebackup.spec
rpmbuild --define="%_topdir `pwd`/build/rpm" -bb build/rpm/SPECS/underscorebackup.spec
mv build/rpm/RPMS/*/*.rpm build/distributions
