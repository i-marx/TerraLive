#!/bin/bash
# Terra server bootstrap v6. Idempotent.
# v5 immortal pusher + GPU-source intake: if a vp9/webm master is published,
# the server transcodes it locally to the broadcast mp4 (with ambience and
# whisper watermark), then swaps it into the immortal loop.
set -e
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq ffmpeg curl gawk fonts-dejavu-core >/dev/null
mkdir -p /opt/terra/bin /opt/terra/segments /etc/terra
touch /etc/terra/stream.env
cat > /opt/terra/bin/terra-pull.sh <<'EOF'
#!/bin/bash
WEBM_URL="https://raw.githubusercontent.com/i-marx/TerraLive/main/streamsrc/terra_stream_source.webm"
MP4_URL="https://github.com/i-marx/TerraLive/releases/download/stream/terra_stream_latest.mp4"
mkdir -p /opt/terra/segments
cd /opt/terra/segments
if curl -fsSL --max-time 900 -o src.tmp "$WEBM_URL" 2>/dev/null; then
  SZ=$(stat -c%s src.tmp 2>/dev/null || echo 0)
  if [ "$SZ" -ge 5000000 ]; then
    NEWSUM=$(sha256sum src.tmp | cut -d' ' -f1)
    OLDSUM=$(cat src.sum 2>/dev/null || echo none)
    if [ "$NEWSUM" != "$OLDSUM" ]; then
      echo "$(date -u) transcoding new GPU source" >> /opt/terra/pull.log
      ffmpeg -y -hide_banner -loglevel error -i src.tmp -filter_complex "[0:v]scale=1920:1080:flags=lanczos,fps=30,setsar=1,drawtext=fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:text='Earth live wallpaper Terra · on Google Play':fontsize=16:fontcolor=white@0.10:x=w-tw-24:y=h-th-18[v]; aevalsrc='0.055*sin(2*PI*55*t)*(0.75+0.25*sin(2*PI*0.031*t)) + 0.038*sin(2*PI*110.4*t)*(0.75+0.25*sin(2*PI*0.047*t+1.3)) + 0.030*sin(2*PI*164.6*t)*(0.72+0.28*sin(2*PI*0.023*t+2.1)) + 0.024*sin(2*PI*221.2*t)*(0.7+0.3*sin(2*PI*0.037*t+0.7)) + 0.012*sin(2*PI*329.6*t)*(0.65+0.35*sin(2*PI*0.019*t+2.8))':s=44100:d=700[dr]; anoisesrc=color=brown:r=44100:d=700:a=0.18[b0]; [b0]lowpass=f=420,volume='0.30+0.16*sin(2*PI*0.013*t)':eval=frame[wa]; [dr][wa]amix=inputs=2:duration=longest:normalize=0,alimiter=limit=0.70[a]" -map "[v]" -map "[a]" -shortest -c:v libx264 -preset veryfast -b:v 6000k -maxrate 6000k -bufsize 12000k -g 60 -pix_fmt yuv420p -c:a aac -b:a 128k -movflags +faststart build.mp4 2>>/opt/terra/pull.log
      if [ -s build.mp4 ]; then
        mv -f build.mp4 pending.mp4
        echo "$NEWSUM" > src.sum
        echo "$(date -u) transcode done, pending swapped" >> /opt/terra/pull.log
      fi
    fi
    rm -f src.tmp
    exit 0
  fi
  rm -f src.tmp
fi
curl -fsSL --max-time 600 -o next.tmp "$MP4_URL" || { rm -f next.tmp; exit 0; }
SZ=$(stat -c%s next.tmp 2>/dev/null || echo 0)
[ "$SZ" -lt 5000000 ] && rm -f next.tmp && exit 0
if [ -f live.mp4 ] && cmp -s next.tmp live.mp4; then rm -f next.tmp; else mv -f next.tmp pending.mp4; fi
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
    case "$D" in ''|N/A) D=600 ;; esac
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
Description=Terra Earth 24/7 YouTube stream (v6)
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
systemctl start terra-pull.service &
echo
echo 'TERRA SERVER READY (v6.2 GPU intake).'
echo 'If a new GPU master exists it is now transcoding in the background (~35 min), then swaps in automatically.'
if grep -q 'YT_KEY=.' /etc/terra/stream.env 2>/dev/null; then echo 'Stream key set: YES'; else echo 'Stream key set: NO'; fi