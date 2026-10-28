// Renders the Play Store icon and feature graphic into store/graphics/ from the launcher
// glyph and the app's own fonts. Run from the repo root: node store/render-graphics.js "$PWD"
const { chromium } = require('playwright');
const path = require('path');
const root = process.argv[2];
const font = (f) => 'data:font/ttf;base64,' + require('fs').readFileSync(path.join(root, 'app/src/main/res/font', f)).toString('base64');
const glyph = `
<g fill="none" stroke="#fff" stroke-linecap="round" stroke-linejoin="round">
 <path stroke-width="5.44" d="M54,33.6 L54,74.4"/>
 <path stroke-width="5.44" d="M40.4,74.4 L67.6,74.4"/>
 <path stroke-width="5.44" d="M33.6,41.76 L74.4,41.76"/>
 <path stroke-width="4.62" d="M33.6,41.76 l-5.44,16.32 a6.26,6.26 0 0,0 11.97,0 z"/>
 <path stroke-width="4.62" d="M74.4,41.76 l5.44,16.32 a6.26,6.26 0 0,1 -11.97,0 z"/>
</g>
<circle cx="54" cy="39.04" r="3.54" fill="#fff"/>`;
const fonts = `<style>
@font-face{font-family:Q;src:url('${font('quicksand_variable.ttf')}');font-weight:300 700}
@font-face{font-family:F;src:url('${font('figtree_variable.ttf')}');font-weight:300 900}
html,body{margin:0;padding:0}</style>`;
const icon = `${fonts}<svg width="512" height="512" viewBox="14 14 80 80" style="display:block;background:#2D7D55">${glyph}</svg>`;
const feature = `${fonts}
<div style="width:1024px;height:500px;background:radial-gradient(circle at 25% 40%,#3A9468 0%,#2D7D55 55%,#24684A 100%);display:flex;align-items:center;gap:56px;padding:0 96px;box-sizing:border-box;color:#fff">
 <svg width="230" height="230" viewBox="18 18 72 72" style="flex:none">${glyph}</svg>
 <div>
  <div style="font-family:Q;font-weight:700;font-size:92px;letter-spacing:-1px;line-height:1">Measured</div>
  <div style="font-family:F;font-weight:500;font-size:34px;line-height:1.3;margin-top:22px;opacity:.95">Your real maintenance calories,<br>from a 2-minute daily check-in</div>
 </div>
</div>`;
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage();
  for (const [html, w, h, out] of [[icon,512,512,'icon-512.png'],[feature,1024,500,'feature-graphic-1024x500.png']]) {
    await p.setViewportSize({ width: w, height: h });
    await p.setContent(html); await p.evaluate(() => document.fonts.ready);
    await p.screenshot({ path: path.join(root, 'store/graphics', out), clip: { x:0, y:0, width:w, height:h } });
  }
  await b.close();
})();
