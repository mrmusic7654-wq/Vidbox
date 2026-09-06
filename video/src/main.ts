// ─── Main: builds all scenes, exposes deterministic window.__seek(t) ─────────
import timeline from "./timeline.json";
import { background } from "./components";
import { sceneCold, sceneCh01, sceneCh02, sceneCh03, sceneCh04, sceneCh05, sceneCh06 } from "./scenes1";
import { sceneCh07, sceneCh08, sceneCh09, sceneCh10, sceneCh11, sceneCh12, sceneCh13, sceneEnd } from "./scenes2";

const TL: any = timeline;
const stage = document.getElementById("stage")!;
stage.style.width = TL.w + "px";
stage.style.height = TL.h + "px";

const bg = background(stage);

// chapter sentence groups by para index
const sentsByPara: { g0: number; g1: number; text: string }[][] = [];
for (const c of TL.chunks) for (const s of c.sents) {
  (sentsByPara[s.para] ??= []).push({ g0: s.g0, g1: s.g1, text: s.text });
}

const sceneBuilders: any[] = [
  sceneCold, sceneCh01, sceneCh02, sceneCh03, sceneCh04, sceneCh05, sceneCh06,
  sceneCh07, sceneCh08, sceneCh09, sceneCh10, sceneCh11, sceneCh12, sceneCh13,
];

type Seg = { t0: number; t1: number; render: (t: number) => void; node: HTMLElement };
const segs: Seg[] = [];

// compute start times sequentially so segments tile the whole timeline
const starts: number[] = [];
for (let p = 0; p < 14; p++) {
  const ss = sentsByPara[p];
  starts.push(p === 0 ? 0 : Math.max(ss[0].g0 - 0.24, starts[p - 1] + 0.05));
}
const endCardStart = Math.max(sentsByPara[13].at(-1)!.g1 + 0.5, starts[13] + 0.05);

for (let p = 0; p < 14; p++) {
  const t0 = starts[p];
  const t1 = p === 13 ? endCardStart : starts[p + 1];
  const node = document.createElement("div");
  Object.assign(node.style, { position: "absolute", inset: "0", zIndex: "3", display: "none" });
  stage.appendChild(node);
  const b = sentsByPara[p].map((s) => s.g0 - t0);
  const render = sceneBuilders[p]({ stage: node, b, dur: t1 - t0 });
  segs.push({ t0, t1, render, node });
}
// endcard
{
  const node = document.createElement("div");
  Object.assign(node.style, { position: "absolute", inset: "0", zIndex: "3", display: "none" });
  stage.appendChild(node);
  const render = sceneEnd({ stage: node, b: [], dur: TL.total - endCardStart });
  segs.push({ t0: endCardStart, t1: TL.total + 1, render, node });
}

let active = -1;
(window as any).__seek = (t: number) => {
  let idx = segs.findIndex((s) => t >= s.t0 && t < s.t1);
  if (idx < 0) idx = t < segs[0].t0 ? 0 : segs.length - 1;
  if (idx !== active) {
    segs.forEach((s, i) => (s.node.style.display = i === idx ? "block" : "none"));
    active = idx;
  }
  bg.seek(t);
  segs[idx].render(t - segs[idx].t0);
};

// font loading + readiness flag
async function boot() {
  const fonts = [
    new FontFace("Anton", "url(fonts/anton-latin-400-normal.woff2)", { weight: "400" }),
    new FontFace("Oswald", "url(fonts/oswald-latin-600-normal.woff2)", { weight: "600" }),
    new FontFace("Archivo Black", "url(fonts/archivo-black-latin-400-normal.woff2)", { weight: "400" }),
  ];
  for (const f of fonts) { (document.fonts as any).add(await f.load()); }
  (window as any).__seek(0);
  (window as any).__ready = true;
}
boot();
