#!/bin/sh

set -e

if [ -d build/unpackeddir ]
then
  	rm -rf build/unpackeddir
fi
mkdir build/unpackeddir
( cd build/unpackeddir ; tar xf $1 --strip-components 1 )

integrationtests/testsuite.pl "`pwd`/build/integrationtests" "`pwd`/build/unpackeddir/bin/underscorebackup"
