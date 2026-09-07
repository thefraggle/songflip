#!/usr/bin/env bash

DEST_DIR="/Users/daniel.notthoff/Library/CloudStorage/OneDrive-OneWorkplace/Desktop"

# 1. Take-Namen ermitteln (automatisch durchnummeriert + Uhrzeit)
if [ -n "$1" ]; then
    TAKE_NAME="$1"
    # Endung .mp4 entfernen falls mit angegeben
    TAKE_NAME="${TAKE_NAME%.mp4}"
else
    # Nächste freie Take-Nummer ermitteln
    N=1
    while true; do
        PAD=$(printf "%02d" $N)
        found=0
        for f in "${DEST_DIR}/take${N}.mp4" "${DEST_DIR}/take_${N}.mp4" "${DEST_DIR}/take_${PAD}.mp4" "${DEST_DIR}/take_${PAD}"_*.mp4; do
            if [ -f "$f" ]; then
                found=1
                break
            fi
        done
        if [ $found -eq 1 ]; then
            ((N++))
        else
            break
        fi
    done
    TIME_STR=$(date +"%H%M")
    TAKE_NAME="take_$(printf "%02d" $N)_${TIME_STR}"
fi

DEVICE_PATH="/sdcard/${TAKE_NAME}.mp4"
DEST_FILE="${DEST_DIR}/${TAKE_NAME}.mp4"

echo "========================================================"
echo "🎬 Starte Aufnahme: ${TAKE_NAME}.mp4"
echo "📱 Bitrate: 16 Mbps (60 FPS)"
echo "👉 Drücke [Strg + C] im Terminal, um den Take zu stoppen."
echo "========================================================"

cleanup() {
    echo ""
    echo "⏳ Aufnahme beendet. Warte 1s auf Android-Videopuffer..."
    sleep 1.2
    echo "⬇️  Ziehe Datei auf deinen Desktop..."
    adb pull "$DEVICE_PATH" "$DEST_FILE"
    adb shell rm -f "$DEVICE_PATH"
    echo ""
    echo "✅ Take erfolgreich gespeichert:"
    echo "   $DEST_FILE"
    echo "========================================================"
    exit 0
}

trap cleanup INT TERM

adb shell screenrecord --bit-rate 16000000 "$DEVICE_PATH"
cleanup
