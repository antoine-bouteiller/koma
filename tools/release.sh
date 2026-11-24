#!/bin/sh
# Builds the release APK for semantic-release's version $1 into ./koma.apk, which the GitHub release carries.
# Needs KOMA_KEYSTORE and the three other KOMA_KEY* upload-key variables.
set -eu
KOMA_VERSION=$1 ./gradlew --no-daemon assembleRelease
cp app/build/outputs/apk/release/app-release.apk koma.apk
