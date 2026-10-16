#!/bin/bash
# One-shot: show stream status, wait for any running transcode, then run
# the render test. Ends with VERDICT GOOD or VERDICT BLACK.
cd /opt/terra/segments 2>/dev/null || { echo "no /opt/terra"; exit 1; }
echo "=== STREAM STATUS ==="
ls -l --time-style=+%H:%M live.mp4 pending.mp4 build.mp4 2>/dev/null
D=$(ffprobe -v error -show_entries format=duration -of csv=p=0 live.mp4 2>/dev/null)
echo "live.mp4 duration: ${D}s  (about 181 = NEW smooth master, about 152 = old stuttery one)"
if pgrep -f "build.mp4" >/dev/null; then
  echo "Transcode of the new master: RUNNING. Waiting for it (checks every 30s)..."
  while pgrep -f "build.mp4" >/dev/null; do sleep 30; echo "  still transcoding $(date +%H:%M)"; done
  echo "Transcode DONE. The smooth master swaps into the stream within ~3 minutes."
else
  echo "No transcode running."
fi
ls -l --time-style=+%H:%M live.mp4 pending.mp4 2>/dev/null
echo ""
echo "=== RENDER TEST (first run ~5 min, downloads Chrome) ==="
curl -fsSL "https://raw.githubusercontent.com/i-marx/TerraLive/main/scripts/render_test.sh?$(date +%s)" | bash
