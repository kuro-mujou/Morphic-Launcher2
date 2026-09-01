#!/bin/bash
# Drive the reference wallpaper studio on the emulator. See README.md — especially the traps.
#
#   drive.sh shape              reshape the emulator to a phone and relaunch the app
#   drive.sh reset              put the emulator's shape back
#   drive.sh shot <name>        screenshot -> <name>.png, plus a downscaled _s and a _p crop of the Style panel
#   drive.sh tabs [n]           scroll the Style tab row n steps (default 1) and shoot
#   drive.sh knob up|down [n]   drag the selected ruler n steps (~37 units each) and shoot
#
# Coordinates are for 1080x2400 at density 400 — what `shape` produces. They are taps; they move if the shape does.
set -u

# adb from Git Bash mangles absolute device paths into Windows ones, silently. Never remove this.
export MSYS_NO_PATHCONV=1

APP=net.smartlauncher.wallpaperstudio
OUT="${REFDRIVE_OUT:-${TMPDIR:-/tmp}/refdrive}"

TAB_ROW_Y=2120
RULER_Y=2240

mkdir -p "$OUT"

shoot() {
    local name="$1"
    adb exec-out screencap -p > "$OUT/$name.png"
    python - "$OUT" "$name" <<'PY'
import sys
try:
    from PIL import Image
except ImportError:
    print("(no Pillow — full-size shot only)"); raise SystemExit
d, name = sys.argv[1], sys.argv[2]
im = Image.open(f"{d}/{name}.png"); im.load(); im = im.convert("RGB")
w, h = im.size
im.resize((w // 3, h // 3), Image.LANCZOS).save(f"{d}/{name}_s.png")
# The Style panel lives in the bottom fifth; blown up 1:1 it is the only way to read the knob's number.
im.crop((0, int(h * 0.80), w, h)).save(f"{d}/{name}_p.png")
print(f"{name}: {w}x{h} -> {d}")
PY
}

case "${1:-}" in
shape)
    adb shell wm size 2400x1080
    adb shell wm density 400
    adb shell settings put system accelerometer_rotation 0
    adb shell settings put system user_rotation 1
    sleep 2
    adb shell am force-stop "$APP"
    sleep 1
    adb shell monkey -p "$APP" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    sleep 4
    adb shell wm size
    ;;
reset)
    adb shell wm size reset
    adb shell wm density reset
    adb shell settings put system accelerometer_rotation 1
    ;;
shot)
    shoot "${2:?name required}"
    ;;
tabs)
    for _ in $(seq 1 "${2:-1}"); do
        adb shell input swipe 950 "$TAB_ROW_Y" 250 "$TAB_ROW_Y" 200
        sleep 1
    done
    shoot "tabs"
    ;;
knob)
    dir="${2:?up or down required}"
    for _ in $(seq 1 "${3:-8}"); do
        # Dragging RIGHT lowers the value. Roughly 37 units per 800px, and not linear near the ends.
        if [ "$dir" = "down" ]; then
            adb shell input swipe 200 "$RULER_Y" 1000 "$RULER_Y" 120
        else
            adb shell input swipe 1000 "$RULER_Y" 200 "$RULER_Y" 120
        fi
    done
    sleep 3
    shoot "knob_$dir"
    echo "Read the number back off ${OUT}/knob_${dir}_p.png — do not assume where it landed."
    ;;
*)
    sed -n '2,10p' "$0"
    exit 1
    ;;
esac
