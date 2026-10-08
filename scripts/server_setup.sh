#!/bin/bash
# Terra stream server bootstrap v5. Idempotent.
# Immortal pusher + duration-offset feeder: one continuous decoder-valid
# timeline forever. No restamping, no resets, no disconnects.
set -e
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq ffmpeg curl gawk >/dev/null
mkdir -p /opt/terra/bin /opt/terra/segments /etc/terra
touch /etc/terra/stream.env
cat > /opt/terra/bin/terra-pull.sh <<'EOF'
#!/bin/bash
URL="https://github.com/i-marx/TerraLive/releases/download/stream/terra_stream_latest.mp4"
mkdir -p /opt/terra/segments
cd /opt/terra/segments
curl -fsSL --max-time 600 -o next.tmp "$URL" || { rm -f next.tmp; exit 0; }
SZ=$(stat -c%s next.tmp 2>/dev/null || echo 0)
[ "$SZ" -lt 5000000 ] && rm -f next.tmp && exit 0
if [ -f live.mp4 ] && cmp -s next.tmp live.mp4; then
  rm -f next.tmp
else
  mv -f next.tmp pending.mp4
fi
EOF
cat > /opt/terra/bin/terra-stream.sh <<'EOF'
#!/bin/bash
cd /opt/terra/segments
YT_KEY=""
[ -f /etc/terra/stream.env ] && . /etc/terra/stream.env
if [ -z "$YT_KEY" ] || [ "$YT_KEY" = "PASTE_KEY_HERE" ]; then sleep 30; exit 1; fi
while [ ! -f live.mp4 ] && [ ! -f pending.mp4 ]; do sleep 10; done
[ -f pending.mp4 ] && mv -f pending.mp4 live.mp4
rm -f feed.ts
mkfifo feed.ts
(
  OFF=0
  while :; do
    [ -f pending.mp4 ] && mv -f pending.mp4 live.mp4
    D=$(ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 live.mp4 2>/dev/null)
    case "$D" in ''|N/A) D=300 ;; esac
    ffmpeg -hide_banner -loglevel error -re -i live.mp4 -c copy -output_ts_offset "$OFF" -f mpegts - 2>>/opt/terra/feeder.log
    OFF=$(awk "BEGIN{printf \"%.3f\", $OFF+$D}")
  done > feed.ts
) &
FEED=$!
trap 'kill $FEED 2>/dev/null' EXIT
ffmpeg -hide_banner -loglevel warning -fflags +genpts -i feed.ts -c copy -f flv "rtmp://a.rtmp.youtube.com/live2/${YT_KEY}"
EOF
chmod 0755 /opt/terra/bin/terra-pull.sh /opt/terra/bin/terra-stream.sh
cat > /etc/systemd/system/terra-stream.service <<'EOF'
[Unit]
Description=Terra Earth 24/7 YouTube stream (immortal pusher v5)
After=network-online.target
Wants=network-online.target
[Service]
ExecStart=/opt/terra/bin/terra-stream.sh
Restart=always
RestartSec=5
[Install]
WantedBy=multi-user.target
EOF
cat > /etc/systemd/system/terra-pull.service <<'EOF'
[Unit]
Description=Pull newest Terra stream segment
[Service]
Type=oneshot
ExecStart=/opt/terra/bin/terra-pull.sh
EOF
cat > /etc/systemd/system/terra-pull.timer <<'EOF'
[Unit]
Description=Check for a fresh Terra segment every 30 minutes
[Timer]
OnBootSec=1min
OnUnitActiveSec=30min
[Install]
WantedBy=timers.target
EOF
systemctl daemon-reload
systemctl enable --now terra-pull.timer
systemctl enable --now terra-stream.service
systemctl restart terra-stream.service
echo
echo 'TERRA SERVER READY (v5 continuous timeline).'
echo "Segment present: $(ls /opt/terra/segments/live.mp4 2>/dev/null || echo 'not yet')"
if grep -q 'YT_KEY=.' /etc/terra/stream.env 2>/dev/null; then echo 'Stream key set: YES'; else echo 'Stream key set: NO'; fi