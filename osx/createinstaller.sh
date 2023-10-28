#!/bin/sh

set -e

export ARCHITECTURE=`uname -m`
if [ ! "$ARCHITECTURE" = "arm64" ]
then
  echo "Must run on arm64"
  exit 1
fi

rm -rf build/installer
mkdir -p "build/installer/Underscore Backup.app/Contents/daemon"

cp -r "osx/Underscore Backup.app" build/installer/

echo "Downloading x64 Java JDK"
if [ ! -d build/x86/amazon-corretto-21.jdk ]
then
  mkdir -p build/x86
  curl -L https://corretto.aws/downloads/latest/amazon-corretto-21-x64-macos-jdk.tar.gz -o build/x86/amazon-corretto-21-x64-macos-jdk.tar.gz
  (cd build/x86 && tar xzf amazon-corretto-21-x64-macos-jdk.tar.gz)
fi

export OLD_PATH=$PATH
export OLD_JAVA_HOME=$JAVA_HOME
export JAVA_HOME=`pwd`/build/x86/amazon-corretto-21.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
rm -rf build/image
rm -rf build/runtime
rm -rf build/jre
arch --arch x86_64 ./gradlew runtime
cp -r build/image "build/installer/Underscore Backup.app/Contents/daemon/x86_64"

export JAVA_HOME=$OLD_JAVA_HOME
export PATH=$OLD_PATH
rm -rf build/image
rm -rf build/runtime
rm -rf build/jre
./gradlew runtime
cp -r build/image "build/installer/Underscore Backup.app/Contents/daemon/$ARCHITECTURE"

(
  cd "build/installer/Underscore Backup.app/Contents/daemon/arm64/lib"

  for lib in *
  do
    if diff -rq "$lib" "../../x86_64/lib/$lib"
    then
      rm -rf "$lib"
      ln -s "../../x86_64/lib/$lib"
    fi
  done
)

export CERT_NAME="Developer ID Application: Underscore Research LLC"

for jar in "build/installer/Underscore Backup.app/Contents/daemon/"*/lib/jffi-*-native.jar
do
  echo "Signing binaries in $jar"
  rm -rf build/repack
  mkdir build/repack
  (
    cd build/repack
    jar xf "../../$jar"
    rm -f "../../$jar"
    for lib in jni/*/*.jnilib
    do
      if [[ ! -L "$lib" ]]
      then
        echo "Signing library $lib"
        codesign --timestamp --force -s "$CERT_NAME" "$lib"
      fi
    done
    jar cmf0 META-INF/MANIFEST.MF "../../$jar" *
  )
done

for jar in "build/installer/Underscore Backup.app/Contents/daemon/"*/lib/argon2-jvm-[0-9]*.jar
do
  echo "Signing binaries in $jar"
  rm -rf build/repack
  mkdir build/repack
  (
    cd build/repack
    jar xf "../../$jar"
    rm -f "../../$jar"
    for lib in */*.dylib
    do
      if [[ ! -L "$lib" ]]
      then
        echo "Signing library $lib"
        codesign --timestamp --force -s "$CERT_NAME" "$lib"
      fi
    done
    jar cmf0 META-INF/MANIFEST.MF "../../$jar" *
  )
done

codesign --force --options runtime --timestamp -s "$CERT_NAME" "build/installer/Underscore Backup.app"
mkdir -p build/distributions
rm -f "build/distributions/underscorebackup-$1.dmg"

(
  cd osx
  npm install
  export SHORTENED=`echo $1 | perl -pe 's/[a-z]//g'`
  perl -pe "s/VERSION/$SHORTENED/" < installer.json > installer-expanded.json
  npx appdmg installer-expanded.json ../build/distributions/underscorebackup-$1.dmg
)

if [ ! -z "$CERT_PASSWORD" ]
then
  xcrun notarytool submit "build/distributions/underscorebackup-$1.dmg" --keychain-profile "$CERT_PASSWORD" --wait
fi
