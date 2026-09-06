// Snapshot specific timeline times for visual QC
import chromium from "@sparticuz/chromium";
import puppeteer from "puppeteer-core";
import path from "node:path";
import { fileURLToPath } from "node:url";
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const times = process.argv.slice(2).map(Number);
const browser = await puppeteer.launch({
  executablePath: await chromium.executablePath(),
  args: chromium.args, headless: chromium.headless,
  defaultViewport: { width: 1920, height: 1080 },
});
const page = await browser.newPage();
await page.goto("file://" + path.join(__dirname, "public", "index.html"));
await page.waitForFunction("window.__ready === true", { timeout: 60000 });
for (let i = 0; i < times.length; i++) {
  await page.evaluate((t) => window.__seek(t), times[i]);
  await page.screenshot({ path: `/tmp/probe_${i}.png` });
  console.log(`probe_${i}.png @ t=${times[i]}`);
}
await browser.close();
