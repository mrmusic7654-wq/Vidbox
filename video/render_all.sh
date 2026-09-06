#!/bin/bash
# Orchestrates full render with N parallel workers
set -e
cd "$(dirname "$0")"
export FFMPEG=$(python3 -c "import imageio_ffmpeg; print(imageio_ffmpeg.get_ffmpeg_exe())")
export LD_LIBRARY_PATH=/tmp/lib:/tmp
export FPS=24
export SCALE=0.75
TOTAL=$(python3 -c "import json;print(json.load(open('src/timeline.json'))['total'])")
NFRAMES=$(python3 -c "import math;print(math.ceil($TOTAL*24))")
NSEG=16
echo "total ${TOTAL}s -> $NFRAMES frames, $NSEG segments"
mkdir -p /tmp/segments
SEGLIST=()
for i in $(seq 0 $((NSEG-1))); do
  S=$(( NFRAMES * i / NSEG ))
  E=$(( NFRAMES * (i+1) / NSEG ))
  SEGLIST+=("/tmp/segments/seg_$(printf '%02d' $i).mp4:$S:$E")
done
# simple 2-worker scheduler
run_two() {
  printf '%s\n' "${SEGLIST[@]}" | xargs -P 2 -I{} bash -c '
    IFS=":" read -r OUT S E <<< "{}"
    if [ ! -f "$OUT.done" ]; then
      node render_segment.mjs "$S" "$E" "$OUT" > "$OUT.log" 2>&1 && touch "$OUT.done" || echo "FAILED $OUT"
    fi'
}
run_two
echo "ALL SEGMENTS DONE"
# concat
ffmpeg=$(python3 -c "import imageio_ffmpeg; print(imageio_ffmpeg.get_ffmpeg_exe())")
: > /tmp/segments/list.txt
for i in $(seq 0 $((NSEG-1))); do echo "file '/tmp/segments/seg_$(printf '%02d' $i).mp4'" >> /tmp/segments/list.txt; done
$ffmpeg -y -f concat -safe 0 -i /tmp/segments/list.txt -c copy /tmp/video_noaudio.mp4 2>>/tmp/concat.log
echo "VIDEO (no audio): /tmp/video_noaudio.mp4"
