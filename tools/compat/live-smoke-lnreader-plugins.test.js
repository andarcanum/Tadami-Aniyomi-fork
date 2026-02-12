const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const {
  runLiveSmokeForPlugin,
  classifyFetchError,
  runLiveSmoke,
  parseArgs,
} = require('./live-smoke-lnreader-plugins');

function makeResponse({
  ok = true,
  status = 200,
  url = 'https://example.org',
  text = '',
  json = null,
  headers = {},
} = {}) {
  return {
    ok,
    status,
    url,
    headers: {
      get(name) {
        return headers[String(name).toLowerCase()] ?? null;
      },
    },
    async text() {
      return text;
    },
    async json() {
      if (json === null) throw new Error('invalid json');
      return json;
    },
  };
}

test('classifyFetchError maps DNS failures to domain_unreachable', () => {
  const code = classifyFetchError(new Error('getaddrinfo ENOTFOUND tl.rulate.ru'));
  assert.equal(code, 'domain_unreachable');
});

test('parseArgs supports langs and ids filters', () => {
  const parsed = parseArgs([
    '--index', 'idx.json',
    '--plugins-dir', 'plugins',
    '--output', 'out.json',
    '--langs', 'english, russian , multisrc',
    '--ids', 'royalroad, scribblehub',
    '--cases', 'cases.json',
    '--concurrency', '4',
  ]);

  assert.deepEqual(parsed.langs, ['english', 'russian', 'multisrc']);
  assert.deepEqual(parsed.ids, ['royalroad', 'scribblehub']);
  assert.equal(parsed.casesPath, 'cases.json');
  assert.equal(parsed.concurrency, 4);
});

test('runLiveSmokeForPlugin reports empty chapters', async () => {
  const plugin = {
    id: 'demo',
    site: 'https://example.org',
  };
  const scriptText = `
    exports.default = {
      popularNovels() {},
      searchNovels() {},
      parseNovel() {},
      parseChapter() {}
    };
  `;

  const fetcher = async (url) => {
    if (url === 'https://example.org') {
      return makeResponse({
        text: '<html><body>ok</body></html>',
        headers: { 'content-type': 'text/html' },
      });
    }
    if (url.startsWith('https://example.org/search/autocomplete')) {
      return makeResponse({
        json: [{ url: '/book/1', title_one: 'Novel', title_two: '' }],
        headers: { 'content-type': 'application/json' },
      });
    }
    if (url === 'https://example.org/book/1') {
      return makeResponse({
        text: '<html><body><h1>No chapters</h1></body></html>',
        headers: { 'content-type': 'text/html' },
      });
    }
    throw new Error(`Unexpected URL ${url}`);
  };

  const result = await runLiveSmokeForPlugin({
    plugin,
    scriptText,
    fetcher,
  });

  assert.equal(result.stages.popular.status, 'pass');
  assert.equal(result.stages.search.status, 'pass');
  assert.equal(result.stages.novel.status, 'pass');
  assert.equal(result.stages.chapters.status, 'fail');
  assert.equal(result.stages.chapters.code, 'empty_chapters');
  assert.equal(result.stages.chapterText.status, 'skip');
  assert.equal(result.stages.chapterText.code, 'chapters_unavailable');
});

test('runLiveSmokeForPlugin passes all stages when chapter text loads', async () => {
  const plugin = {
    id: 'demo',
    site: 'https://example.org',
  };
  const scriptText = `
    exports.default = {
      popularNovels() {},
      searchNovels() {},
      parseNovel() {},
      parseChapter() {}
    };
  `;

  const fetcher = async (url) => {
    if (url === 'https://example.org') {
      return makeResponse({
        text: '<html><body>ok</body></html>',
        headers: { 'content-type': 'text/html' },
      });
    }
    if (url.startsWith('https://example.org/search/autocomplete')) {
      return makeResponse({
        json: [{ url: '/book/2', title_one: 'Novel', title_two: '' }],
        headers: { 'content-type': 'application/json' },
      });
    }
    if (url === 'https://example.org/book/2') {
      return makeResponse({
        text: '<a class="chapter" href="/chapter/7">Chapter 7</a>',
        headers: { 'content-type': 'text/html' },
      });
    }
    if (url === 'https://example.org/chapter/7') {
      return makeResponse({
        text: '<div class="content-text">Hello from chapter content with enough text length</div>',
        headers: { 'content-type': 'text/html' },
      });
    }
    throw new Error(`Unexpected URL ${url}`);
  };

  const result = await runLiveSmokeForPlugin({
    plugin,
    scriptText,
    fetcher,
  });

  assert.equal(result.stages.popular.status, 'pass');
  assert.equal(result.stages.search.status, 'pass');
  assert.equal(result.stages.novel.status, 'pass');
  assert.equal(result.stages.chapters.status, 'pass');
  assert.equal(result.stages.chapterText.status, 'pass');
  assert.equal(result.chapterCount, 1);
  assert.ok(result.sampleChapterTextLength > 0);
});

test('runLiveSmokeForPlugin marks search stage as skipped when handler is missing', async () => {
  const plugin = {
    id: 'demo',
    site: 'https://example.org',
  };
  const scriptText = `
    exports.default = {
      popularNovels() {},
      parseNovel() {},
      parseChapter() {}
    };
  `;

  const fetcher = async (url) => {
    if (url === 'https://example.org') {
      return makeResponse({ text: '<html><body>ok</body></html>' });
    }
    throw new Error(`Unexpected URL ${url}`);
  };

  const result = await runLiveSmokeForPlugin({
    plugin,
    scriptText,
    fetcher,
  });

  assert.equal(result.stages.popular.status, 'pass');
  assert.equal(result.stages.search.status, 'skip');
  assert.equal(result.stages.search.code, 'missing_handler');
  assert.equal(result.stages.novel.status, 'skip');
  assert.equal(result.stages.novel.code, 'search_unavailable');
  assert.equal(result.stages.chapters.status, 'skip');
  assert.equal(result.stages.chapters.code, 'novel_unavailable');
  assert.equal(result.stages.chapterText.status, 'skip');
  assert.equal(result.stages.chapterText.code, 'chapters_unavailable');
});

test('runLiveSmokeForPlugin marks chapters as missing_handler when parseNovel handler is missing', async () => {
  const plugin = {
    id: 'demo',
    site: 'https://example.org',
  };
  const scriptText = `
    exports.default = {
      popularNovels() {},
      searchNovels() {},
      parseChapter() {}
    };
  `;

  const fetcher = async (url) => {
    if (url === 'https://example.org') {
      return makeResponse({ text: '<html><body>ok</body></html>' });
    }
    if (url.startsWith('https://example.org/search/autocomplete')) {
      return makeResponse({ json: [{ url: '/book/4' }] });
    }
    throw new Error(`Unexpected URL ${url}`);
  };

  const result = await runLiveSmokeForPlugin({
    plugin,
    scriptText,
    fetcher,
    query: 'love',
  });

  assert.equal(result.stages.popular.status, 'pass');
  assert.equal(result.stages.search.status, 'pass');
  assert.equal(result.stages.novel.status, 'skip');
  assert.equal(result.stages.novel.code, 'missing_handler');
  assert.equal(result.stages.chapters.status, 'skip');
  assert.equal(result.stages.chapters.code, 'missing_handler');
  assert.equal(result.stages.chapterText.status, 'skip');
  assert.equal(result.stages.chapterText.code, 'chapters_unavailable');
});

test('runLiveSmokeForPlugin falls back to next search template when first is invalid', async () => {
  const plugin = {
    id: 'demo',
    site: 'https://example.org',
  };
  const scriptText = `
    exports.default = {
      popularNovels() {},
      searchNovels() {},
      parseNovel() {}
    };
  `;

  const seenSearchUrls = [];
  const fetcher = async (url) => {
    if (url === 'https://example.org') {
      return makeResponse({ text: '<html><body>ok</body></html>' });
    }
    if (url.startsWith('https://example.org/search/autocomplete')) {
      seenSearchUrls.push(url);
      return makeResponse({ text: '<html>not-json</html>' });
    }
    if (url.startsWith('https://example.org/search?query=')) {
      seenSearchUrls.push(url);
      return makeResponse({ json: [{ url: '/book/3' }] });
    }
    if (url === 'https://example.org/book/3') {
      return makeResponse({
        text: '<a class="chapter" href="/chapter/9">Chapter 9</a>',
      });
    }
    throw new Error(`Unexpected URL ${url}`);
  };

  const result = await runLiveSmokeForPlugin({
    plugin,
    scriptText,
    fetcher,
    query: 'love',
  });

  assert.deepEqual(seenSearchUrls, [
    'https://example.org/search/autocomplete?query=love',
    'https://example.org/search?query=love',
  ]);
  assert.equal(result.stages.search.status, 'pass');
  assert.equal(result.stages.novel.status, 'pass');
  assert.equal(result.stages.chapters.status, 'pass');
});

test('runLiveSmokeForPlugin skips dependent stages when search has no candidates', async () => {
  const plugin = {
    id: 'demo',
    site: 'https://example.org',
  };
  const scriptText = `
    exports.default = {
      popularNovels() {},
      searchNovels() {},
      parseNovel() {},
      parseChapter() {}
    };
  `;

  const fetcher = async (url) => {
    if (url === 'https://example.org') {
      return makeResponse({
        text: '<html><body>ok</body></html>',
        headers: { 'content-type': 'text/html' },
      });
    }
    if (url.startsWith('https://example.org/search/autocomplete')) {
      return makeResponse({
        json: [],
        headers: { 'content-type': 'application/json' },
      });
    }
    if (url.startsWith('https://example.org/search?query=')) {
      return makeResponse({
        json: [],
        headers: { 'content-type': 'application/json' },
      });
    }
    if (url.startsWith('https://example.org/search?keyword=')) {
      return makeResponse({
        json: [],
        headers: { 'content-type': 'application/json' },
      });
    }
    throw new Error(`Unexpected URL ${url}`);
  };

  const result = await runLiveSmokeForPlugin({
    plugin,
    scriptText,
    fetcher,
  });

  assert.equal(result.stages.search.status, 'fail');
  assert.equal(result.stages.search.code, 'empty_search_results');
  assert.equal(result.stages.novel.status, 'skip');
  assert.equal(result.stages.novel.code, 'search_unavailable');
  assert.equal(result.stages.chapters.status, 'skip');
  assert.equal(result.stages.chapters.code, 'novel_unavailable');
  assert.equal(result.stages.chapterText.status, 'skip');
  assert.equal(result.stages.chapterText.code, 'chapters_unavailable');
});

test('runLiveSmokeForPlugin uses explicit case novel and chapter URLs', async () => {
  const plugin = {
    id: 'demo',
    site: 'https://example.org',
  };
  const scriptText = `
    exports.default = {
      popularNovels() {},
      parseNovel() {},
      parseChapter() {}
    };
  `;

  const fetcher = async (url) => {
    if (url === 'https://example.org') {
      return makeResponse({ text: '<html><body>ok</body></html>' });
    }
    if (url === 'https://example.org/book/42') {
      return makeResponse({ text: '<html><body><h1>Novel</h1></body></html>' });
    }
    if (url === 'https://example.org/chapter/42') {
      return makeResponse({ text: '<div>Chapter content that is definitely long enough.</div>' });
    }
    throw new Error(`Unexpected URL ${url}`);
  };

  const result = await runLiveSmokeForPlugin({
    plugin,
    scriptText,
    pluginCase: {
      novelPath: '/book/42',
      chapterPath: '/chapter/42',
    },
    fetcher,
  });

  assert.equal(result.stages.search.status, 'skip');
  assert.equal(result.stages.search.code, 'missing_handler');
  assert.equal(result.stages.novel.status, 'pass');
  assert.equal(result.stages.chapters.status, 'pass');
  assert.equal(result.stages.chapterText.status, 'pass');
  assert.equal(result.novelUrl, 'https://example.org/book/42');
  assert.equal(result.sampleChapterUrl, 'https://example.org/chapter/42');
});

function mkTmpDir(name) {
  return fs.mkdtempSync(path.join(os.tmpdir(), `${name}-`));
}

function writeJson(filePath, value) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}

test('runLiveSmoke aggregates report with injected fetcher', async () => {
  const tempDir = mkTmpDir('live-smoke-agg');
  const pluginRoot = path.join(tempDir, '.js', 'plugins', 'english');
  fs.mkdirSync(pluginRoot, { recursive: true });

  fs.writeFileSync(
    path.join(pluginRoot, 'one.js'),
    'Object.defineProperty(exports,"__esModule",{value:!0});exports.default={popularNovels(){},searchNovels(){},parseNovel(){},parseChapter(){}};',
    'utf8',
  );
  fs.writeFileSync(
    path.join(pluginRoot, 'two.js'),
    'Object.defineProperty(exports,"__esModule",{value:!0});exports.default={popularNovels(){},searchNovels(){},parseNovel(){},parseChapter(){}};',
    'utf8',
  );

  const indexPath = path.join(tempDir, '.dist', 'plugins.min.json');
  writeJson(indexPath, [
    {
      id: 'one',
      name: 'One',
      site: 'https://one.example',
      lang: 'English',
      version: '1.0.0',
      url: 'https://raw.githubusercontent.com/acme/lnreader-plugins/plugins/v3.0.0/.js/src/plugins/english/one.js',
    },
    {
      id: 'two',
      name: 'Two',
      site: 'https://two.example',
      lang: 'English',
      version: '1.0.0',
      url: 'https://raw.githubusercontent.com/acme/lnreader-plugins/plugins/v3.0.0/.js/src/plugins/english/two.js',
    },
  ]);

  const fetcher = async (url) => {
    if (url === 'https://one.example') {
      return makeResponse({ text: '<html>ok</html>' });
    }
    if (url === 'https://two.example') {
      return makeResponse({ text: '<html>ok</html>' });
    }
    if (url.startsWith('https://one.example/search/autocomplete')) {
      return makeResponse({ json: [{ url: '/book/1' }] });
    }
    if (url.startsWith('https://two.example/search/autocomplete')) {
      return makeResponse({ json: [] });
    }
    if (url.startsWith('https://two.example/search?query=')) {
      return makeResponse({ json: [] });
    }
    if (url.startsWith('https://two.example/search?keyword=')) {
      return makeResponse({ json: [] });
    }
    if (url === 'https://one.example/book/1') {
      return makeResponse({
        text: '<a class="chapter" href="/chapter/1">Chapter 1</a>',
      });
    }
    if (url === 'https://one.example/chapter/1') {
      return makeResponse({
        text: '<div class="content-text">Sample content for chapter one is long enough.</div>',
      });
    }
    throw new Error(`Unexpected URL ${url}`);
  };

  const result = await runLiveSmoke({
    indexPath,
    pluginBaseDir: path.join(tempDir, '.js', 'plugins'),
    query: 'love',
    timeoutMs: 5000,
    limit: 0,
    concurrency: 2,
    fetcher,
  });

  assert.equal(result.summary.totalPlugins, 2);
  assert.equal(result.summary.passedAllStages, 1);
  assert.equal(result.summary.failedPlugins, 1);
  assert.equal(result.stageFailures.search, 1);
  assert.equal(result.failureCodes.empty_search_results, 1);
});

test('runLiveSmoke aggregates failure codes once per plugin per code', async () => {
  const tempDir = mkTmpDir('live-smoke-codes');
  const pluginRoot = path.join(tempDir, '.js', 'plugins', 'english');
  fs.mkdirSync(pluginRoot, { recursive: true });

  fs.writeFileSync(
    path.join(pluginRoot, 'dupe.js'),
    'Object.defineProperty(exports,"__esModule",{value:!0});exports.default={popularNovels(){},searchNovels(){}};',
    'utf8',
  );

  const indexPath = path.join(tempDir, '.dist', 'plugins.min.json');
  writeJson(indexPath, [
    {
      id: 'dupe',
      name: 'Dupe',
      site: 'https://dupe.example',
      lang: 'English',
      version: '1.0.0',
      url: 'https://raw.githubusercontent.com/acme/lnreader-plugins/plugins/v3.0.0/.js/src/plugins/english/dupe.js',
    },
  ]);

  const fetcher = async (url) => {
    if (url === 'https://dupe.example') {
      return makeResponse({ ok: false, status: 503 });
    }
    if (url.startsWith('https://dupe.example/search')) {
      return makeResponse({ ok: false, status: 503 });
    }
    throw new Error(`Unexpected URL ${url}`);
  };

  const result = await runLiveSmoke({
    indexPath,
    pluginBaseDir: path.join(tempDir, '.js', 'plugins'),
    query: 'love',
    timeoutMs: 5000,
    limit: 0,
    concurrency: 1,
    fetcher,
  });

  assert.equal(result.summary.totalPlugins, 1);
  assert.equal(result.summary.failedPlugins, 1);
  assert.equal(result.stageFailures.popular, 1);
  assert.equal(result.stageFailures.search, 1);
  assert.equal(result.plugins[0].stages.popular.code, 'request_failed');
  assert.equal(result.plugins[0].stages.search.code, 'request_failed');
  assert.equal(result.failureCodes.request_failed, 1);
});

test('runLiveSmoke filters by langs and ids', async () => {
  const tempDir = mkTmpDir('live-smoke-filter');
  const enRoot = path.join(tempDir, '.js', 'plugins', 'english');
  const ruRoot = path.join(tempDir, '.js', 'plugins', 'russian');
  fs.mkdirSync(enRoot, { recursive: true });
  fs.mkdirSync(ruRoot, { recursive: true });

  fs.writeFileSync(
    path.join(enRoot, 'one.js'),
    'Object.defineProperty(exports,"__esModule",{value:!0});exports.default={popularNovels(){},searchNovels(){},parseNovel(){},parseChapter(){}};',
    'utf8',
  );
  fs.writeFileSync(
    path.join(ruRoot, 'two.js'),
    'Object.defineProperty(exports,"__esModule",{value:!0});exports.default={popularNovels(){},searchNovels(){},parseNovel(){},parseChapter(){}};',
    'utf8',
  );

  const indexPath = path.join(tempDir, '.dist', 'plugins.min.json');
  writeJson(indexPath, [
    {
      id: 'one',
      name: 'One',
      site: 'https://one.example',
      lang: 'english',
      version: '1.0.0',
      url: 'https://raw.githubusercontent.com/acme/lnreader-plugins/plugins/v3.0.0/.js/src/plugins/english/one.js',
    },
    {
      id: 'two',
      name: 'Two',
      site: 'https://two.example',
      lang: 'russian',
      version: '1.0.0',
      url: 'https://raw.githubusercontent.com/acme/lnreader-plugins/plugins/v3.0.0/.js/src/plugins/russian/two.js',
    },
  ]);

  const fetcher = async (url) => {
    if (url === 'https://one.example') return makeResponse({ text: '<html>ok</html>' });
    if (url.startsWith('https://one.example/search')) return makeResponse({ json: [{ url: '/book/1' }] });
    if (url === 'https://one.example/book/1') return makeResponse({ text: '<a href="/chapter/1">Chapter</a>' });
    if (url === 'https://one.example/chapter/1') return makeResponse({ text: '<div>Long enough chapter text content.</div>' });
    throw new Error(`Unexpected URL ${url}`);
  };

  const result = await runLiveSmoke({
    indexPath,
    pluginBaseDir: path.join(tempDir, '.js', 'plugins'),
    langs: ['english'],
    ids: ['one'],
    fetcher,
  });

  assert.equal(result.summary.totalPlugins, 1);
  assert.equal(result.plugins[0].id, 'one');
  assert.deepEqual(result.inputs.langs, ['english']);
  assert.deepEqual(result.inputs.ids, ['one']);
});
