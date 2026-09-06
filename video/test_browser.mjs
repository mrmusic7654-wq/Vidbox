import chromium from '@sparticuz/chromium';
import puppeteer from 'puppeteer-core';
const exePath = await chromium.executablePath();
console.log('exe:', exePath);
const browser = await puppeteer.launch({
  executablePath: exePath, args: chromium.args, headless: chromium.headless,
  defaultViewport: { width: 1280, height: 720 }
});
const page = await browser.newPage();
await page.setContent('<div style="width:100%;height:100%;background:#E8F1F5;display:flex;align-items:center;justify-content:center"><h1 style="font-size:90px;color:#1F3A5F">HELLO VIDBOX</h1></div>');
await page.screenshot({ path: '/tmp/test_shot.png' });
await browser.close();
console.log('screenshot ok');
