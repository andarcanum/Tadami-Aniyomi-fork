const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const {
  analyzeCompatibility,
  SUPPORTED_MODULES,
} = require('./check-lnreader-plugins');

function mkTmpDir(name) {
  return fs.mkdtempSync(path.join(os.tmpdir(), `${name}-`));
}

function writeJson(filePath, value) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}

test('analyzeCompatibility counts index fields and sha256 state', () => {
  const tempDir = mkTmpDir('compat-test-index');
  const pluginRoot = path.join(tempDir, '.js', 'plugins', 'english');
  fs.mkdirSync(pluginRoot, { recursive: true });
  fs.writeFileSync(
    path.join(pluginRoot, 'good.js'),
    'Object.defineProperty(exports,"__esModule",{value:!0});exports.default={id:"good"};',
    'utf8',
  );

  const indexPath = path.join(tempDir, '.dist', 'plugins.min.json');
  writeJson(indexPath, [
    {
      id: 'good',
      name: 'Good',
      site: 'https://example.org',
      lang: 'English',
      version: '1.2.3',
      url: 'https://raw.githubusercontent.com/acme/lnreader-plugins/plugins/v3.0.0/.js/src/plugins/english/good.js',
    },
    {
      id: '',
      name: 'Broken',
      site: 'https://example.org',
      lang: 'English',
      version: '1.0.0',
      url: 'https://raw.githubusercontent.com/acme/lnreader-plugins/plugins/v3.0.0/.js/src/plugins/english/missing.js',
      sha256: 'abc',
    },
  ]);

  const result = analyzeCompatibility({
    indexPath,
    pluginBaseDir: path.join(tempDir, '.js', 'plugins'),
  });

  assert.equal(result.summary.totalEntries, 2);
  assert.equal(result.summary.entriesMissingSha256, 1);
  assert.equal(result.summary.entriesWithInvalidRequiredFields, 1);
  assert.equal(result.summary.resolvedScriptsMissingLocally, 1);
});

test('analyzeCompatibility flags unsupported modules and fetchProto usage', () => {
  const tempDir = mkTmpDir('compat-test-runtime');
  const pluginRoot = path.join(tempDir, '.js', 'plugins', 'english');
  fs.mkdirSync(pluginRoot, { recursive: true });

  fs.writeFileSync(
    path.join(pluginRoot, 'unsupported.js'),
    'const x=require("@libs/unknownModule");Object.defineProperty(exports,"__esModule",{value:!0});exports.default={id:"unsupported"};',
    'utf8',
  );
  fs.writeFileSync(
    path.join(pluginRoot, 'proto.js'),
    'const f=require("@libs/fetch");f.fetchProto();Object.defineProperty(exports,"__esModule",{value:!0});exports.default={id:"proto"};',
    'utf8',
  );
  fs.writeFileSync(
    path.join(pluginRoot, 'nodefault.js'),
    'const d=require("dayjs");module.exports={id:"nodefault"};',
    'utf8',
  );

  const indexPath = path.join(tempDir, '.dist', 'plugins.min.json');
  writeJson(indexPath, [
    {
      id: 'unsupported',
      name: 'Unsupported',
      site: 'https://example.org',
      lang: 'English',
      version: '1.0.0',
      url: 'https://raw.githubusercontent.com/acme/lnreader-plugins/plugins/v3.0.0/.js/src/plugins/english/unsupported.js',
    },
    {
      id: 'proto',
      name: 'Proto',
      site: 'https://example.org',
      lang: 'English',
      version: '1.0.0',
      url: 'https://raw.githubusercontent.com/acme/lnreader-plugins/plugins/v3.0.0/.js/src/plugins/english/proto.js',
    },
    {
      id: 'nodefault',
      name: 'NoDefault',
      site: 'https://example.org',
      lang: 'English',
      version: '1.0.0',
      url: 'https://raw.githubusercontent.com/acme/lnreader-plugins/plugins/v3.0.0/.js/src/plugins/english/nodefault.js',
    },
  ]);

  const result = analyzeCompatibility({
    indexPath,
    pluginBaseDir: path.join(tempDir, '.js', 'plugins'),
  });

  assert.ok(SUPPORTED_MODULES.has('@libs/isAbsoluteUrl'));
  assert.deepEqual(result.runtime.unsupportedModulesByPlugin.unsupported, ['@libs/unknownModule']);
  assert.deepEqual(result.runtime.pluginsUsingFetchProto, ['proto']);
  assert.deepEqual(result.runtime.pluginsMissingDefaultExport, ['nodefault']);
  assert.equal(result.summary.incompatiblePluginCount, 2);
});

test('analyzeCompatibility exposes staged contract diagnostics', () => {
  const tempDir = mkTmpDir('compat-test-contract');
  const pluginRoot = path.join(tempDir, '.js', 'plugins', 'english');
  fs.mkdirSync(pluginRoot, { recursive: true });

  fs.writeFileSync(
    path.join(pluginRoot, 'minimal.js'),
    'Object.defineProperty(exports,"__esModule",{value:!0});exports.default={id:"minimal",popularNovels(){},searchNovels(){},parseNovel(){},parseChapter(){}};',
    'utf8',
  );

  const indexPath = path.join(tempDir, '.dist', 'plugins.min.json');
  writeJson(indexPath, [
    {
      id: 'minimal',
      name: 'Minimal',
      site: 'https://example.org',
      lang: 'English',
      version: '1.0.0',
      url: 'https://raw.githubusercontent.com/acme/lnreader-plugins/plugins/v3.0.0/.js/src/plugins/english/minimal.js',
    },
  ]);

  const result = analyzeCompatibility({
    indexPath,
    pluginBaseDir: path.join(tempDir, '.js', 'plugins'),
  });

  assert.equal(result.summary.totalEntries, 1);
  assert.ok(result.contract);
  assert.equal(result.contract.pluginsWithFailedStages, 0);
  assert.deepEqual(result.plugins[0].stages, {
    popular: 'pass',
    search: 'pass',
    novel: 'pass',
    chapters: 'pass',
    chapterText: 'pass',
  });
});
