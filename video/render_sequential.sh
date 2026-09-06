#!/bin/bash
# Sequential renderer: one worker, full CPU (2 cores) -> ~3fps vs 1.5fps parallel
cd "$(dirname "$0")"
mkdir -p /tmp/segments
export FFMPEG=$(python3 -c "import imageio_ffmpeg; print(imageio_ffmpeg.get_ffmpeg_exe())")
export LD_LIBRARY_PATH=/tmp/lib:/tmp
export FPS=24
export SCALE=0.75
TOTAL=$(python3 -c "import json;print(json.load(open('src/timeline.json'))['total'])")
NFRAMES=$(python3 -c "import math;print(math.ceil($TOTAL*24))")
echo "sequential render: $NFRAMES frames in 16 segments, one worker"
for i in $(seq 0 15); do
  S=$(( NFRAMES * i / 16 ))
  E=$(( NFRAMES * (i+1) / 16 ))
  OUT="/tmp/segments/seg_$(printf '%02d' $i).mp4"
  if [ -f "$OUT.done" ]; then echo "skip $OUT (done)"; continue; fi
  echo "=== rendering $OUT [$S..$E] $(date +%H:%M:%S)"
  node render_segment.mjs "$S" "$E" "$OUT" > "$OUT.log" 2>&1 && touch "$OUT.done" || { echo "FAILED $OUT"; exit 1; }
done
echo "ALL_SEGMENTS_DONE"
