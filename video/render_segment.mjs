// Frame renderer v2: renders at scaled resolution, upscales in encode
import chromium from "@sparticuz/chromium";
import puppeteer from "puppeteer-core";
import { spawn } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const FPS = parseInt(process.env.FPS || "24");
const SCALE = parseFloat(process.env.SCALE || "0.75"); // 0.75 -> 1440x810
const frameStart = parseInt(process.argv[2] ?? "0");
const frameEnd = parseInt(process.argv[3] ?? "0");
const outFile = process.argv[4] ?? "/tmp/seg.mp4";
const t0 = frameStart / FPS;
const VW = Math.round(1920 * SCALE), VH = Math.round(1080 * SCALE);

console.log(`[render] frames ${frameStart}..${frameEnd - 1} (${((frameEnd - frameStart) / FPS).toFixed(1)}s) @ ${VW}x${VH} -> ${outFile}`);

const exePath = await chromium.executablePath();
const browser = await puppeteer.launch({
  executablePath: exePath,
  args: [...chromium.args, "--force-color-profile=srgb", "--hide-scrollbars", "--disable-lcd-text"],
  headless: chromium.headless,
  defaultViewport: { width: VW, height: VH, deviceScaleFactor: 1 },
});
const page = await browser.newPage();
await page.goto("file://" + path.join(__dirname, "public", "index.html"));
await page.evaluate((s) => { document.getElementById("stage").style.transform = `scale(${s})`; }, SCALE);
await page.waitForFunction("window.__ready === true", { timeout: 60000 });

const ff = spawn(process.env.FFMPEG || "ffmpeg", [
  "-y", "-f", "image2pipe", "-framerate", String(FPS), "-i", "-",
  "-vf", `scale=1920:1080:flags=lanczos`,
  "-c:v", "libx264", "-preset", "ultrafast", "-crf", "18",
  "-pix_fmt", "yuv420p", "-movflags", "+faststart", outFile,
], { stdio: ["pipe", "inherit", "inherit"] });

const t_start = Date.now();
for (let f = frameStart; f < frameEnd; f++) {
  const t = f / FPS;
  await page.evaluate((tt) => window.__seek(tt), t);
  const buf = await page.screenshot({ type: "jpeg", quality: 88, optimizeForSpeed: true, captureBeyondViewport: false });
  if (!ff.stdin.write(buf)) await new Promise((r) => ff.stdin.once("drain", r));
  if ((f - frameStart) % 240 === 0) {
    const el = (Date.now() - t_start) / 1000;
    console.log(`[render] ${f - frameStart + 1}/${frameEnd - frameStart} frames, ${((f - frameStart + 1) / el).toFixed(1)} fps, elapsed ${el.toFixed(0)}s`);
  }
}
ff.stdin.end();
await new Promise((res) => ff.on("close", (c) => { console.log(`[render] ffmpeg exit ${c}`); res(); }));
await browser.close();
console.log(`[render] DONE ${outFile} in ${((Date.now() - t_start) / 1000).toFixed(0)}s`);
