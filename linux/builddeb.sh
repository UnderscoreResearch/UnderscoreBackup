#!/bin/sh
set -e
set -x

./gradlew jpackageImage -Pinstaller_type=deb
rm -rf build/installerimage
mkdir -p build/distributions
mkdir -p build/installerimage/opt/underscorebackup
mkdir -p build/installerimage/etc/systemd/system
mkdir -p build/installerimage/DEBIAN
mv build/jpackage/underscorebackup/* build/installerimage/opt/underscorebackup
cp README.md build/installerimage/opt/underscorebackup
cp scripts/underscorebackup.service build/installerimage/etc/systemd/system/underscorebackup.service
sed s/VERSION/$VERSION/ < linux/control > build/installerimage/DEBIAN/control
cp linux/postinst linux/prerm build/installerimage/DEBIAN
( cd build/distributions ; dpkg-deb --build --root-owner-group ../installerimage )
mv build/installerimage.deb build/distributions/underscorebackup_$VERSION-1_amd64.deb
