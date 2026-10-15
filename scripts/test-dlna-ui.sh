#!/usr/bin/env bash
set -uo pipefail

collect_screenshots() {
    mkdir -p ui-screenshots
    adb pull /sdcard/Android/data/zju.bangdream.ktv.casting/files/. ui-screenshots/ || true
}
trap collect_screenshots EXIT

chmod +x gradlew
./gradlew connectedDebugAndroidTest \
    -Ptarget_abis=x86_64 \
    -Papp_version_name=0.0.0-ui-test \
    -Pandroid.testInstrumentationRunnerArguments.class=zju.bangdream.ktv.casting.DlnaProxyUiTest
