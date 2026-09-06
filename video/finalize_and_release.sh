#!/bin/bash
# Waits for render, stitches, muxes narration, zips, publishes GitHub Release with the MP4
set -e
cd "$(dirname "$0")"
FF=$(python3 -c "import imageio_ffmpeg; print(imageio_ffmpeg.get_ffmpeg_exe())")
BRANCH=arena/01a07543-vidbox
TAG=bank-collapse-video-v1
FINAL=/home/user/Vidbox/FINAL_This_Is_What_Always_Happens_Before_A_Bank_Collapse.mp4

echo "== waiting for 16 render segments =="
while true; do
  N=$(ls /tmp/segments/*.done 2>/dev/null | wc -l)
  echo "  $N/16 done ($(date +%H:%M:%S))"
  [ "$N" -eq 16 ] && break
  sleep 60
done

echo "== concat segments =="
: > /tmp/segments/list.txt
for i in $(seq 0 15); do echo "file '/tmp/segments/seg_$(printf '%02d' $i).mp4'" >> /tmp/segments/list.txt; done
$FF -y -f concat -safe 0 -i /tmp/segments/list.txt -c copy /tmp/video_noaudio.mp4 2>/tmp/concat.log
echo "concat ok: $(du -h /tmp/video_noaudio.mp4 | cut -f1)"

echo "== build narration track at exact offsets =="
python3 - <<'EOF' > /tmp/filter.txt
import json
TL = json.load(open("src/timeline.json"))
parts = []
for c in TL["chunks"]:
    ms = int(c["t0"]*1000)
    parts.append(f"[{c['idx']}]adelay={ms}:{ms}[a{c['idx']}]")
mix = "".join(f"[a{i}]" for i in range(10))
print(";".join(parts) + f";{mix}amix=inputs=10:normalize=0[out]")
EOF
ARGS=()
for i in $(seq 0 9); do ARGS+=(-i "../audio/part$(printf '%02d' $i).mp3"); done
$FF -y "${ARGS[@]}" -filter_complex "$(cat /tmp/filter.txt)" -map "[out]" -t "$(python3 -c "import json;print(json.load(open('src/timeline.json'))['total'])")" -ar 48000 /tmp/narration_track.wav 2>/tmp/narr.log
echo "narration ok: $(du -h /tmp/narration_track.wav | cut -f1)"

echo "== mux final mp4 =="
$FF -y -i /tmp/video_noaudio.mp4 -i /tmp/narration_track.wav -c:v copy -c:a aac -b:a 192k -shortest "$FINAL" 2>/tmp/mux.log
echo "FINAL: $(du -h $FINAL | cut -f1)"

echo "== zip package =="
cd /home/user/Vidbox
mkdir -p /tmp/pkg/Vidbox_Package/audio /tmp/pkg/Vidbox_Package/visuals
cp "$FINAL" /tmp/pkg/Vidbox_Package/
cp 01_STYLE_ANALYSIS.md 02_NEW_STORY_SCRIPT.md 03_narration_plain.txt 04_VISUAL_STORYBOARD.md /tmp/pkg/Vidbox_Package/ || true
cp audio/*.mp3 /tmp/pkg/Vidbox_Package/audio/
cp visuals/*.png /tmp/pkg/Vidbox_Package/visuals/
rm -f /home/user/Vidbox/Vidbox_Package.zip
cd /tmp/pkg && zip -qr /home/user/Vidbox/Vidbox_Package.zip Vidbox_Package
echo "ZIP: $(du -h /home/user/Vidbox/Vidbox_Package.zip | cut -f1)"

echo "== publish GitHub Release =="
cd /home/user/Vidbox
gh release view "$TAG" >/dev/null 2>&1 && gh release upload "$TAG" "$FINAL" --clobber || \
  gh release create "$TAG" "$FINAL" --target "$BRANCH" \
    --title "This Is What 'Always' Happens Right Before a Bank Collapse — Final MP4 (1080p)" \
    --notes "Full code-animated explainer video in The Infographics Show style.
- 1920x1080, 24 fps, H.264 + AAC narration
- Runtime ~16 min 18 s
- Animation authored in TypeScript/HTML/CSS (see /video), rendered frame-by-frame
- Script, style analysis, narration audio and storyboard included in the repo
- Companion piece to: https://youtu.be/EOXle1B_dZs"
echo "RELEASE PUBLISHED: tag $TAG"
echo "ALL_DONE"
