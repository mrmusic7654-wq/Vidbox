#!/bin/bash
# Waits for render completion, stitches segments, muxes narration, packages zip + email draft
set -e
cd "$(dirname "$0")"
FF=$(python3 -c "import imageio_ffmpeg; print(imageio_ffmpeg.get_ffmpeg_exe())")

echo "== waiting for 16 segments =="
while true; do
  N=$(ls /tmp/segments/*.done 2>/dev/null | wc -l)
  echo "  $N/16 done ($(date +%H:%M:%S))"
  [ "$N" -eq 16 ] && break
  sleep 60
done

echo "== concat =="
: > /tmp/segments/list.txt
for i in $(seq 0 15); do echo "file '/tmp/segments/seg_$(printf '%02d' $i).mp4'" >> /tmp/segments/list.txt; done
$FF -y -f concat -safe 0 -i /tmp/segments/list.txt -c copy /tmp/video_noaudio.mp4 2>/tmp/concat.log

echo "== mux narration =="
# build exact-length narration track: concat with 0.45s gaps + 2.5s tail, matching timeline.py
python3 - <<'EOF'
from pydub import AudioSegment
import json, os
TL = json.load(open("src/timeline.json"))
total_ms = int(TL["total"]*1000)
track = AudioSegment.silent(duration=total_ms)
for c in TL["chunks"]:
    seg = AudioSegment.from_mp3(f"../audio/{c['audio']}")
    track = track.overlay(seg, position=int(c["t0"]*1000))
track.export("/tmp/narration_track.mp3", bitrate="192k")
print("narration track:", len(track)/1000, "s")
EOF

$FF -y -i /tmp/video_noaudio.mp4 -i /tmp/narration_track.mp3 -c:v copy -c:a aac -b:a 192k -shortest /home/user/Vidbox/FINAL_This_Is_What_Always_Happens_Before_A_Bank_Collapse.mp4

echo "== package =="
cd /home/user/Vidbox
mkdir -p /tmp/pkg/Vidbox_Package
cp FINAL_This_Is_What_Always_Happens_Before_A_Bank_Collapse.mp4 /tmp/pkg/Vidbox_Package/
cp 01_STYLE_ANALYSIS.md 02_NEW_STORY_SCRIPT.md 03_narration_plain.txt 04_VISUAL_STORYBOARD.md /tmp/pkg/Vidbox_Package/ 2>/dev/null || true
mkdir -p /tmp/pkg/Vidbox_Package/audio /tmp/pkg/Vidbox_Package/visuals
cp audio/*.mp3 /tmp/pkg/Vidbox_Package/audio/
cp visuals/*.png /tmp/pkg/Vidbox_Package/visuals/
cd /tmp/pkg && zip -qr /home/user/Vidbox/Vidbox_Package.zip Vidbox_Package
echo "PACKAGE: /home/user/Vidbox/Vidbox_Package.zip"
ls -la /home/user/Vidbox/*.mp4 /home/user/Vidbox/*.zip
