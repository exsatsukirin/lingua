#!/usr/bin/env bash
# Developer helper: drives the Lingua UI on the connected device through adb.
#
# Usage:
#   tools/drive_ui.sh dump                        print the visible text tree with tap coordinates
#   tools/drive_ui.sh tap-text "Test connection"  tap the first node containing that text
#   tools/drive_ui.sh type "some text"            type into the focused field (spaces handled)
#   tools/drive_ui.sh setup-profile               create the mock API profile and save it
#   tools/drive_ui.sh screenshot FILE             capture the screen
#
# Coordinates come from `android layout` and are only stable for this test device
# (Waydroid 2560x1536 @360dpi).
set -uo pipefail

SDK="${ANDROID_SDK_ROOT:-$HOME/.android-sdk}"
LAYOUT="android --sdk=$SDK layout"

PY_DUMP='import json,sys
def walk(nodes):
    for e in nodes:
        t = e.get("text") or ""
        cd = e.get("content-desc") or e.get("contentDesc") or ""
        it = e.get("interactions") or []
        if t or cd:
            flags = ""
            if "CLICKABLE" in it: flags += "C"
            if "EDITABLE" in it: flags += "E"
            print("{} [{}] {}".format(e.get("center"), flags, t or cd))
        walk(e.get("children") or [])
walk(json.load(sys.stdin))'

PY_FIND='import json,sys
needle = sys.argv[1]
hits = []
def walk(nodes):
    for e in nodes:
        t = e.get("text") or ""
        cd = e.get("content-desc") or e.get("contentDesc") or ""
        if needle in t or needle in cd:
            hits.append(e.get("center"))
        walk(e.get("children") or [])
walk(json.load(sys.stdin))
print(hits[0].strip("[]").replace(",", " ") if hits else "")'

cmd_dump() { $LAYOUT 2>/dev/null | python3 -c "$PY_DUMP"; }

center_of() { $LAYOUT 2>/dev/null | python3 -c "$PY_FIND" "$1"; }

cmd_tap_text() {
  local coords
  coords=$(center_of "$1")
  if [ -z "$coords" ]; then echo "not found: $1" >&2; exit 2; fi
  # shellcheck disable=SC2086
  adb shell input tap $coords
  echo "tapped '$1' at $coords"
}

cmd_type() { adb shell input text "$(printf '%s' "$1" | sed 's/ /%s/g')"; }

cmd_setup_profile() {
  adb shell am force-stop com.lingua.app
  adb shell am start -n com.lingua.app/.MainActivity >/dev/null
  sleep 5
  adb shell input tap 90 451; sleep 2
  adb shell input swipe 1370 1300 1370 600 300; sleep 2
  cmd_tap_text "添加配置"; sleep 2
  adb shell input tap 1280 462; sleep 1; cmd_type "Mock";                     sleep 1; adb shell input keyevent 111
  adb shell input tap 1280 677; sleep 1; cmd_type "http://127.0.0.1:8765/v1"; sleep 1; adb shell input keyevent 111
  adb shell input tap 1280 898; sleep 1; cmd_type "test-key";                 sleep 1; adb shell input keyevent 111
  adb shell input tap 1280 1112; sleep 1; cmd_type "mock-translate";          sleep 1; adb shell input keyevent 111
  sleep 1
  cmd_tap_text "保存"; sleep 2
  echo "profile saved"
}

case "${1:-}" in
  dump) cmd_dump ;;
  tap-text) cmd_tap_text "$2" ;;
  type) cmd_type "$2" ;;
  setup-profile) cmd_setup_profile ;;
  screenshot) android --sdk="$SDK" screen capture --output="$2" ;;
  *) echo "unknown command: ${1:-}" >&2; exit 1 ;;
esac
