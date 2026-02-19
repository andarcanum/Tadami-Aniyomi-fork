// Build script for bundling JS libraries for NovelJsRuntime
const esbuild = require('esbuild');
const path = require('path');

const ASSETS_DIR = path.resolve(__dirname, '../../app/src/main/assets/js');

async function build() {
  // Bundle dayjs — IIFE that assigns to globalThis.__dayjs
  await esbuild.build({
    entryPoints: [path.join(__dirname, 'dayjs-entry.js')],
    bundle: true,
    minify: true,
    format: 'iife',
    globalName: '__dayjs',
    platform: 'neutral',
    mainFields: ['main', 'module'],
    outfile: path.join(ASSETS_DIR, 'dayjs.bundle.js'),
    target: ['es5'],
  });
  console.log('dayjs bundle built');

  // Bundle htmlparser2 — IIFE that assigns to globalThis.__htmlparser2
  await esbuild.build({
    entryPoints: [path.join(__dirname, 'htmlparser2-entry.js')],
    bundle: true,
    minify: true,
    format: 'iife',
    globalName: '__htmlparser2',
    platform: 'neutral',
    mainFields: ['main', 'module'],
    outfile: path.join(ASSETS_DIR, 'htmlparser2.bundle.js'),
    target: ['es5'],
  });
  console.log('htmlparser2 bundle built');
}

build().catch((err) => {
  console.error(err);
  process.exit(1);
});
