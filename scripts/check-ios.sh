#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
MODE="${1:-all}"
case "$MODE" in
  all|native|ui) ;;
  *) echo "Usage: $0 [all|native|ui]" >&2; exit 2 ;;
esac
xcodebuild -version
DEVICE_ID="$(xcrun simctl list devices available -j | python3 -c 'import json,sys; ds=json.load(sys.stdin)["devices"]; print(next(d["udid"] for group in ds.values() for d in group if d.get("isAvailable") and "iPhone" in d["name"]))')"
xcrun simctl boot "$DEVICE_ID" 2>/dev/null || true
xcrun simctl bootstatus "$DEVICE_ID" -b
if [ "$MODE" != "ui" ]; then
if [ "$(uname -m)" = "arm64" ]; then
  ./gradlew :shared:iosSimulatorArm64Test --device "$DEVICE_ID"
else
  ./gradlew :shared:iosX64Test --device "$DEVICE_ID"
fi
fi
if [ "$MODE" != "native" ]; then
rm -rf iosApp/build/TestResults.xcresult
collect_diagnostics() {
  local result=$?
  if [ "$result" -ne 0 ]; then
    mkdir -p iosApp/build/diagnostics
    xcrun simctl spawn "$DEVICE_ID" log show --last 10m --style compact \
      --predicate 'process == "PetCare"' > iosApp/build/diagnostics/PetCare.log 2>&1 || true
    xcrun xcresulttool export diagnostics --path iosApp/build/TestResults.xcresult \
      --output-path iosApp/build/diagnostics/xcresult || true
    tail -100 iosApp/build/diagnostics/PetCare.log
  fi
  exit "$result"
}
trap collect_diagnostics EXIT
xcodebuild -project iosApp/PetCare.xcodeproj -scheme PetCare \
  -configuration Debug -sdk iphonesimulator \
  -destination "platform=iOS Simulator,id=$DEVICE_ID" \
  -derivedDataPath iosApp/build/DerivedData -resultBundlePath iosApp/build/TestResults.xcresult \
  CODE_SIGNING_ALLOWED=NO test

fi
