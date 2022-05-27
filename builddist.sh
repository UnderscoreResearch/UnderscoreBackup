#!/bin/sh

set -e

rm -rf build

( cd webui ; npm run build )

./gradlew allDistTest

