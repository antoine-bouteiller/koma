#!/bin/sh
# Publishes Koma from CI: a new APK to the AppDrop link when Android needs one, then a signed update.
# Needs APPDROP_URL, APPDROP_MANAGE_KEY, EXACT_SIGNING_KEY_DIR and the four EXACT_KEY* upload-key variables.
set -eu
apk=target/android/app/outputs/apk/release/app-release.apk

bun exact.mjs deploy --platform android --dirty --json > "${RUNNER_TEMP:-/tmp}/plan.json"
if bun -e 'const p = await Bun.file(process.argv[1]).json(); process.exit(p.rows.some((r) => r.kind === "binary" && r.platform === "android") ? 0 : 1)' "${RUNNER_TEMP:-/tmp}/plan.json"; then
  # AppDrop refuses a versionCode it already holds.
  published=$(curl -fsS "https://appdrop.app/api/drops/${APPDROP_URL##*/}" | bun -e 'console.log((await Bun.stdin.json()).version)')
  EXACT_UPDATE_TRUST=production EXACT_UPDATE_GENESIS=1 EXACT_BUILD_NUMBER=$((published + 1)) \
    bun exact.mjs android --release --no-install --json
  # Print only public fields: the CLI's JSON can carry the link's update key.
  bunx appdrop@0.9.0 "$apk" --update "$APPDROP_URL" --json |
    bun -e 'const r = await Bun.stdin.json(); console.log("published", r.url, "version", r.version)'
fi
bun exact.mjs deploy --platform android --dirty --yes
