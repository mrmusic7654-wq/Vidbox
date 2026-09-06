// ─── 5-minute condensed cut: 5 independent 58s parts ────────────────────────
import tl5 from "./timeline5.json";
import { background } from "./components";
import { sceneCold, sceneCh01, sceneCh05, sceneCh06 } from "./scenes1";
import { sceneCh07, sceneCh13 } from "./scenes2";

const TL: any = tl5;
const stage = document.getElementById("stage")!;
stage.style.width = TL.w + "px";
stage.style.height = TL.h + "px";
const bg = background(stage);

const builders: any = {
  sceneCold, sceneCh01, sceneCh05, sceneCh06, sceneCh07, sceneCh13,
};

// The page renders ONE part at a time: ?part=N (1..5)
const qp = new URLSearchParams(location.search);
const PART = parseInt(qp.get("part") || "1") - 1;
const meta = TL.parts[PART];

let render: ((t: number) => void) | null = null;
{
  const node = document.createElement("div");
  Object.assign(node.style, { position: "absolute", inset: "0", zIndex: "3" });
  stage.appendChild(node);
  render = builders[meta.scene]({ stage: node, b: meta.beats, dur: meta.partDur });
}

(window as any).__seek = (t: number) => {
  bg.seek(t);
  render!(t);
};

async function boot() {
  const fonts = [
    new FontFace("Anton", "url(fonts/anton-latin-400-normal.woff2)", { weight: "400" }),
    new FontFace("Oswald", "url(fonts/oswald-latin-600-normal.woff2)", { weight: "600" }),
    new FontFace("Archivo Black", "url(fonts/archivo-black-latin-400-normal.woff2)", { weight: "400" }),
  ];
  for (const f of fonts) (document.fonts as any).add(await f.load());
  (window as any).__seek(0);
  (window as any).__ready = true;
}
boot();
