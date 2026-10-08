#!/bin/bash
# Terra stream server bootstrap v3. Idempotent. Concat-loop edition:
# continuous timestamps across loops so YouTube never drops the stream.
set -e
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq ffmpeg curl >/dev/null
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
# Concat playlist repeats the same segment 2000x with continuously
# increasing timestamps. Restart only when a fresh segment arrives.
mkdir -p /opt/terra/segments
cd /opt/terra/segments
while :; do
  [ -f pending.mp4 ] && mv -f pending.mp4 live.mp4
  YT_KEY=""
  [ -f /etc/terra/stream.env ] && . /etc/terra/stream.env
  if [ -f live.mp4 ] && [ -n "$YT_KEY" ] && [ "$YT_KEY" != "PASTE_KEY_HERE" ]; then
    : > list.txt
    for i in $(seq 1 2000); do echo "file '/opt/terra/segments/live.mp4'" >> list.txt; done
    ffmpeg -hide_banner -loglevel warning -re -f concat -safe 0 -i list.txt -c copy -f flv "rtmp://a.rtmp.youtube.com/live2/${YT_KEY}" &
    FPID=$!
    while kill -0 $FPID 2>/dev/null; do
      if [ -f pending.mp4 ]; then
        kill $FPID 2>/dev/null
        wait $FPID 2>/dev/null
        break
      fi
      sleep 15
    done
  else
    sleep 10
  fi
  sleep 2
done
EOF
chmod 0755 /opt/terra/bin/terra-pull.sh /opt/terra/bin/terra-stream.sh
cat > /etc/systemd/system/terra-stream.service <<'EOF'
[Unit]
Description=Terra Earth 24/7 YouTube stream
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
echo 'TERRA SERVER READY (v3 concat loop).'
echo "Segment present: $(ls /opt/terra/segments/live.mp4 2>/dev/null || echo 'not yet')"
if grep -q 'YT_KEY=.' /etc/terra/stream.env 2>/dev/null; then echo 'Stream key set: YES'; else echo 'Stream key set: NO'; fi