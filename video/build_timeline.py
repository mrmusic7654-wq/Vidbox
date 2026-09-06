#!/usr/bin/env python3
"""Builds timeline.json: global clock, per-chunk sentence beats, shots, chapter map.
The chunk packing MUST mirror the TTS chunking used for audio generation."""
import json, re, os
from mutagen.mp3 import MP3

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FPS = 30
PAD = 0.45          # gap between narration chunks (s)
TAIL = 2.5          # after last chunk (endcard)
W, H = 1920, 1080

paras = [p.strip() for p in open(f"{ROOT}/03_narration_plain.txt").read().split("\n\n") if p.strip()]
# paragraph i -> chapter i (para 0 = cold open, 1..13 = chapters)
sents = []  # {chunk-agnostic list, later assigned}
for pi, p in enumerate(paras):
    for s in re.split(r'(?<=[.?!]) ', p):
        if s: sents.append({"para": pi, "text": s})

# ---- repack into chunks with the SAME algorithm used for TTS (limit 1492) ----
chunks, cur = [], []
for s in sents:
    cand = " ".join(cur + [s["text"]])
    if len(cand) <= 1492: cur.append(s["text"])
    else: chunks.append(cur); cur = [s["text"]]
if cur: chunks.append(cur)
assert len(chunks) == 10, f"expected 10 chunks, got {len(chunks)}"

# durations
durs = [MP3(f"{ROOT}/audio/part{i:02d}.mp3").info.length for i in range(10)]

# assign sentences to chunks in order + compute char offsets
T = 0.0
chunk_objs = []
si = 0
for ci, texts in enumerate(chunks):
    t0 = T
    cs = []
    cch = 0
    total = sum(len(t) for t in texts)
    d = durs[ci]
    for txt in texts:
        sent = sents[si]
        off = (cch / total) * d
        dur = (len(txt) / total) * d
        cs.append({"para": sent["para"], "text": txt,
                   "g0": round(t0 + off, 3), "g1": round(t0 + off + dur, 3)})
        cch += len(txt); si += 1
    chunk_objs.append({"idx": ci, "audio": f"part{ci:02d}.mp3", "t0": round(t0,3), "dur": round(d,3), "sents": cs})
    T += d + PAD

TOTAL = T - PAD + TAIL
NFRAMES = int(round(TOTAL * FPS))

# chapters: para -> meta
chapters = [
  {"id":"cold","title":"","n":0},
  {"id":"ch01","title":"THE $500 BILLION HOLE HIDING IN EVERY BANK","n":1},
  {"id":"ch02","title":"THE EMPTY OFFICE TIME BOMB","n":2},
  {"id":"ch03","title":"1,788 BANKS ARE ALREADY OVER THE LINE","n":3},
  {"id":"ch04","title":"\u201cEXTEND AND PRETEND\u201d: THE ZOMBIE LOANS","n":4},
  {"id":"ch05","title":"THE BUFFETT SIGNAL","n":5},
  {"id":"ch06","title":"THE RICH DON\u2019T STAND IN LINE","n":6},
  {"id":"ch07","title":"42 BILLION DOLLARS IN 24 HOURS","n":7},
  {"id":"ch08","title":"THE 167-YEAR-OLD BANK THAT DIED IN A WEEK","n":8},
  {"id":"ch09","title":"THE SECRET LIST OF DYING BANKS","n":9},
  {"id":"ch10","title":"YOUR $250,000 PROMISE","n":10},
  {"id":"ch11","title":"TOO BIG TO FAIL, TOO SMALL TO SAVE","n":11},
  {"id":"ch12","title":"HOW YOUR MONEY GETS TRAPPED","n":12},
  {"id":"ch13","title":"WHY NOBODY WARNS YOU","n":13},
]

out = {"fps":FPS, "w":W, "h":H, "total":round(TOTAL,3), "nframes":NFRAMES,
       "pad":PAD, "tail":TAIL, "chunks":chunk_objs, "chapters":chapters}
json.dump(out, open(f"{ROOT}/video/src/timeline.json","w"), indent=1)
print(f"TOTAL {TOTAL:.1f}s = {TOTAL/60:.2f} min, frames {NFRAMES} @ {FPS}fps")
for c in chunk_objs:
    print(f"chunk {c['idx']}: t0={c['t0']:7.2f} dur={c['dur']:6.2f}s sents={len(c['sents'])} paras={sorted(set(s['para'] for s in c['sents']))}")
