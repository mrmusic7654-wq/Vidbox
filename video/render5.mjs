// Renders one 58s part at SCALE/FPS, muxes narration -> PART_N.mp4 (exactly partLen seconds)
import chromium from "@sparticuz/chromium";
import puppeteer from "puppeteer-core";
import { spawn } from "node:child_process";
import path from "node:path";
import fs from "node:fs";
import { fileURLToPath } from "node:url";
const __dirname = path.dirname(fileURLToPath(import.meta.url));

const PART = parseInt(process.argv[2] ?? "1");           // 1..5
const FPS = parseFloat(process.env.FPS || "15");
const SCALE = parseFloat(process.env.SCALE || "0.5");
const PARTLEN = parseFloat(process.env.PARTLEN || "58");
const OUT = process.argv[3] ?? `/tmp/part_${PART}.mp4`;
const NFRAMES = Math.round(PARTLEN * FPS);
const VW = Math.round(1920 * SCALE), VH = Math.round(1080 * SCALE);

console.log(`[part${PART}] ${NFRAMES} frames @ ${VW}x${VH} -> ${OUT}`);
const browser = await puppeteer.launch({
  executablePath: await chromium.executablePath(),
  args: [...chromium.args, "--force-color-profile=srgb", "--hide-scrollbars", "--disable-lcd-text"],
  headless: chromium.headless,
  defaultViewport: { width: VW, height: VH },
});
const page = await browser.newPage();
await page.goto(`file://${path.join(__dirname, "public", "index5.html")}?part=${PART}`);
await page.evaluate((s) => { document.getElementById("stage").style.transform = `scale(${s})`; }, SCALE);
await page.waitForFunction("window.__ready === true", { timeout: 60000 });

const ff = spawn(process.env.FFMPEG || "ffmpeg", [
  "-y", "-f", "image2pipe", "-framerate", String(FPS), "-i", "-",
  "-vf", "scale=1920:1080:flags=lanczos",
  "-c:v", "libx264", "-preset", "ultrafast", "-crf", "18",
  "-pix_fmt", "yuv420p", "-movflags", "+faststart", `/tmp/part_${PART}_video.mp4`,
], { stdio: ["pipe", "inherit", "inherit"] });

const t0 = Date.now();
for (let f = 0; f < NFRAMES; f++) {
  await page.evaluate((tt) => window.__seek(tt), f / FPS);
  const buf = await page.screenshot({ type: "jpeg", quality: 88, optimizeForSpeed: true, captureBeyondViewport: false });
  if (!ff.stdin.write(buf)) await new Promise((r) => ff.stdin.once("drain", r));
  if (f % 300 === 0) console.log(`[part${PART}] ${f}/${NFRAMES} (${((f + 1) / ((Date.now() - t0) / 1000)).toFixed(1)} fps)`);
}
ff.stdin.end();
await new Promise((r) => ff.on("close", r));
await browser.close();

// mux narration starting at 1.0s, hard-cap at PARTLEN
const AUD = path.join(__dirname, "..", "audio", `p5_part${PART}.mp3`);
const adm = spawn(process.env.FFMPEG || "ffmpeg", [
  "-y", "-i", `/tmp/part_${PART}_video.mp4`, "-i", AUD,
  "-filter_complex", `[1:a]adelay=1000|1000,apad[a]`,
  "-map", "0:v", "-map", "[a]", "-t", String(PARTLEN),
  "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", OUT,
], { stdio: ["inherit", "inherit", "inherit"] });
await new Promise((r) => adm.on("close", r));
console.log(`[part${PART}] DONE ${OUT} in ${((Date.now() - t0) / 1000).toFixed(0)}s`);
