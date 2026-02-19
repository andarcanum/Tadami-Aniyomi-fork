const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const {
  parseArgs,
  selectPlugins,
  collectHttpFixtures,
} = require('./collect-http-fixtures');

function mkTmpDir(name) {
  return fs.mkdtempSync(path.join(os.tmpdir(), `${name}-`));
}

function writeJson(filePath, value) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}

function makeResponse({
  ok = true,
  status = 200,
  text = '',
  contentType = 'text/html; charset=utf-8',
} = {}) {
  return {
    ok,
    status,
    headers: {
      get(name) {
        if (String(name).toLowerCase() === 'content-type') {
          return contentType;
        }
        return null;
      },
    },
    async text() {
      return text;
    },
  };
}

test('parseArgs supports ids, limit, query and timeout', () => {
  const parsed = parseArgs([
    '--index', 'idx.json',
    '--output-dir', 'out',
    '--ids', 'royalroad, scribblehub',
    '--limit', '15',
    '--query', 'dragon',
    '--timeout-ms', '8000',
  ]);

  assert.equal(parsed.indexPath, 'idx.json');
  assert.equal(parsed.outputDir, 'out');
  assert.deepEqual(parsed.ids, ['royalroad', 'scribblehub']);
  assert.equal(parsed.limit, 15);
  assert.equal(parsed.query, 'dragon');
  assert.equal(parsed.timeoutMs, 8000);
});

test('selectPlugins preserves id order when ids filter is provided', () => {
  const input = [
    { id: 'one' },
    { id: 'two' },
    { id: 'three' },
  ];

  const selected = selectPlugins(input, ['three', 'one'], 20);
  assert.deepEqual(selected.map((it) => it.id), ['three', 'one']);
});

test('collectHttpFixtures writes manifests and body fixtures', async () => {
  const tempDir = mkTmpDir('collect-fixtures');
  const indexPath = path.join(tempDir, 'plugins.min.json');
  const outputDir = path.join(tempDir, 'fixtures');

  writeJson(indexPath, [
    {
      id: 'royalroad',
      name: 'Royal Road',
      lang: 'english',
      site: 'https://www.royalroad.com',
    },
  ]);

  const calls = [];
  const fetcher = async (url) => {
    calls.push(url);
    if (url === 'https://www.royalroad.com/') {
      return makeResponse({ text: '<html>site</html>', contentType: 'text/html' });
    }
    if (url.includes('/search/')) {
      return makeResponse({ text: '{"items":[]}', contentType: 'application/json' });
    }
    throw new Error(`Unexpected URL ${url}`);
  };

  const result = await collectHttpFixtures({
    indexPath,
    outputDir,
    ids: ['royalroad'],
    timeoutMs: 3000,
    fetcher,
  });

  assert.equal(result.selectedCount, 1);
  assert.equal(result.plugins.length, 1);
  assert.equal(calls.length, 4);

  const pluginDir = path.join(outputDir, 'royalroad');
  assert.ok(fs.existsSync(path.join(pluginDir, 'manifest.json')));
  assert.ok(fs.existsSync(path.join(outputDir, 'fixtures-summary.json')));
  assert.ok(
    fs.readdirSync(pluginDir).some((name) => name.startsWith('site.') && name !== 'manifest.json'),
    'site fixture file should be present',
  );
});
