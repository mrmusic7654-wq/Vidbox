#!/usr/bin/env bash
set -uo pipefail
./scripts/verify-gradle.sh :app:connectedDebugAndroidTest :data:connectedDebugAndroidTest
status=$?
# Also launch the production Application, not only Hilt's instrumentation Application.
apk=app/build/outputs/apk/debug/app-debug.apk
reports=app/build/reports/device-smoke
if [[ -f "$apk" ]]; then
    mkdir -p "$reports"
    adb logcat -d -s Vidbox:V AndroidRuntime:E > "$reports/test-logcat.txt"
    adb install -r "$apk" >/dev/null
    adb shell pm clear com.vidbox.app.debug >/dev/null
    adb shell am start -W -n com.vidbox.app.debug/com.vidbox.MainActivity > "$reports/launch.txt"
    adb shell uiautomator dump /sdcard/vidbox-window.xml > /dev/null
    adb pull /sdcard/vidbox-window.xml "$reports/window.xml" >/dev/null
    adb exec-out screencap -p > "$reports/home.png"
    python3 - "$reports/window.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
nodes = list(root.iter('node'))
assert any(n.get('package') == 'com.vidbox.app.debug' for n in nodes), 'Production activity is not visible'
assert any(n.get('text') == 'Vidbox' for n in nodes), 'Vidbox home screen did not launch'
print('::notice title=Production launch::Installed debug APK and launched the real VidboxApplication/MainActivity successfully on the device. Screenshot and UI hierarchy are in the device-smoke report.')
PY
    if [[ $? -ne 0 ]]; then status=1; fi
else
    status=1
fi
exit "$status"
