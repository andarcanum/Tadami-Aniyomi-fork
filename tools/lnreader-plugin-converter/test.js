const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');

const { convertRepo } = require('./converter');

async function run() {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'lnreader-converter-'));
  const pluginPath = path.join(tmpDir, 'plugin.js');
  fs.writeFileSync(pluginPath, 'console.log("hello");\n');

  const inputPath = path.join(tmpDir, 'input.json');
  const outputPath = path.join(tmpDir, 'repo.json');

  const input = [
    {
      id: 'test.plugin',
      name: 'Test Plugin',
      site: 'https://example.com',
      lang: 'en',
      version: 1,
      url: pluginPath,
      iconUrl: 'https://example.com/icon.png',
      hasSettings: true,
    },
  ];
  fs.writeFileSync(inputPath, JSON.stringify(input, null, 2));

  await convertRepo({ inputPath, outputPath });

  const output = JSON.parse(fs.readFileSync(outputPath, 'utf8'));
  assert.strictEqual(output.length, 1);
  assert.strictEqual(output[0].id, 'test.plugin');
  assert.strictEqual(output[0].sha256.length, 64);
  assert.strictEqual(output[0].hasSettings, true);
  assert.strictEqual(output[0].iconUrl, 'https://example.com/icon.png');

  console.log('OK');
}

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
