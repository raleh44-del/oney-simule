// Rend la pub en MP4 1920x1080 (H.264 + AAC, lisible par les TV et les lecteurs
// USB des magasins), avec la musique générée par musique.py.
//
//   NODE_PATH=$(npm root -g) node pub/render.js                 -> la vidéo
//   NODE_PATH=$(npm root -g) node pub/render.js --images 3,7.5  -> des images fixes
//
// Prérequis : playwright (Chromium), ffmpeg, python3 avec numpy.
const { chromium } = require('playwright');
const { spawn, spawnSync } = require('child_process');
const path = require('path');
const fs = require('fs');
const os = require('os');
const { pathToFileURL } = require('url');

const FPS = 30;
const PAGE = path.join(__dirname, 'tv-edenwood-50-qled.html');
const SORTIE = path.join(__dirname, 'tv-edenwood-50-qled.mp4');

(async () => {
  const navigateur = await chromium.launch();
  const page = await navigateur.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 });
  await page.goto(pathToFileURL(PAGE).href + '?rendu');
  await page.evaluate(() => document.fonts.ready);
  const cdp = await page.context().newCDPSession(page);
  const capture = async t => {
    await page.evaluate(t => window.frame(t), t);
    const { data } = await cdp.send('Page.captureScreenshot', { format: 'png', optimizeForSpeed: true });
    return Buffer.from(data, 'base64');
  };

  const i = process.argv.indexOf('--images');
  if (i > 0){
    const dossier = process.argv[i + 2] || path.join(__dirname, 'images');
    fs.mkdirSync(dossier, { recursive: true });
    for (const t of process.argv[i + 1].split(',').map(Number)){
      fs.writeFileSync(path.join(dossier, `t${t}.png`), await capture(t));
    }
    await navigateur.close();
    return;
  }

  const duree = await page.evaluate(() => window.DUREE);
  const musique = path.join(os.tmpdir(), 'pub-musique.wav');
  const py = spawnSync('python3', [path.join(__dirname, 'musique.py'), musique], { stdio: 'inherit' });
  if (py.status !== 0) throw new Error('musique.py a échoué');
  const ffmpeg = spawn('ffmpeg', [
    '-y', '-loglevel', 'error',
    '-f', 'image2pipe', '-c:v', 'png', '-framerate', String(FPS), '-i', '-',
    '-i', musique,
    '-map', '0:v', '-map', '1:a',
    '-vf', 'scale=out_color_matrix=bt709:out_range=tv,format=yuv420p',
    '-c:v', 'libx264', '-preset', 'slow', '-crf', '16', '-profile:v', 'high', '-level:v', '4.1',
    '-g', String(FPS * 2),
    '-colorspace', 'bt709', '-color_primaries', 'bt709', '-color_trc', 'bt709', '-color_range', 'tv',
    '-c:a', 'aac', '-b:a', '192k', '-t', String(duree),
    '-movflags', '+faststart', SORTIE,
  ], { stdio: ['pipe', 'inherit', 'inherit'] });

  const total = Math.round(duree * FPS);
  for (let n = 0; n < total; n++){
    const png = await capture(n / FPS);
    if (!ffmpeg.stdin.write(png)) await new Promise(r => ffmpeg.stdin.once('drain', r));
    if (n % FPS === 0) process.stdout.write(`\r${n / FPS} / ${duree} s`);
  }
  ffmpeg.stdin.end();
  await new Promise((ok, ko) => ffmpeg.on('close', c => c ? ko(new Error('ffmpeg ' + c)) : ok()));
  await navigateur.close();
  console.log('\n' + SORTIE);
})();
