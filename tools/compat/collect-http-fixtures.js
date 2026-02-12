#!/usr/bin/env node
const fs = require('node:fs');
const path = require('node:path');

const SEARCH_TEMPLATES = [
  '/search/autocomplete?query={query}',
  '/search?query={query}',
  '/search?keyword={query}',
];

function usage() {
  const script = path.basename(process.argv[1] || 'collect-http-fixtures.js');
  console.log(
    `Usage: ${script} --index <plugins.min.json> --output-dir <dir> [--ids <csv>] [--limit <n>] [--query <text>] [--timeout-ms <n>] [--no-pretty]`,
  );
}

function parseCsv(value) {
  return String(value || '')
    .split(',')
    .map((it) => it.trim())
    .filter(Boolean);
}

function parseArgs(args) {
  const parsed = {
    query: 'love',
    timeoutMs: 15000,
    limit: 20,
    pretty: true,
  };
  for (let i = 0; i < args.length; i += 1) {
    const arg = args[i];
    if (arg === '--index') {
      parsed.indexPath = args[i + 1];
      i += 1;
      continue;
    }
    if (arg === '--output-dir') {
      parsed.outputDir = args[i + 1];
      i += 1;
      continue;
    }
    if (arg === '--ids') {
      parsed.ids = parseCsv(args[i + 1]);
      i += 1;
      continue;
    }
    if (arg === '--limit') {
      parsed.limit = Number(args[i + 1] || 20);
      i += 1;
      continue;
    }
    if (arg === '--query') {
      parsed.query = args[i + 1];
      i += 1;
      continue;
    }
    if (arg === '--timeout-ms') {
      parsed.timeoutMs = Number(args[i + 1] || 15000);
      i += 1;
      continue;
    }
    if (arg === '--no-pretty') {
      parsed.pretty = false;
      continue;
    }
    if (arg === '--help' || arg === '-h') {
      parsed.help = true;
      continue;
    }
    throw new Error(`Unknown argument: ${arg}`);
  }
  return parsed;
}

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function writeJson(filePath, value, pretty = true) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  const json = pretty ? JSON.stringify(value, null, 2) : JSON.stringify(value);
  fs.writeFileSync(filePath, `${json}\n`, 'utf8');
}

function sanitizeSegment(value) {
  return String(value || '')
    .trim()
    .replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_')
    .replace(/\s+/g, '_');
}

function resolveUrl(base, value) {
  const raw = String(value || '').trim();
  if (!raw) return null;
  try {
    return base ? new URL(raw, base).toString() : new URL(raw).toString();
  } catch {
    return null;
  }
}

async function fetchWithTimeout(url, fetcher, timeoutMs) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort('timeout'), timeoutMs);
  try {
    return await fetcher(url, { redirect: 'follow', signal: controller.signal });
  } finally {
    clearTimeout(timeout);
  }
}

function selectPlugins(indexEntries, ids = [], limit = 20) {
  if (!Array.isArray(indexEntries)) return [];
  if (Array.isArray(ids) && ids.length > 0) {
    const byId = new Map(indexEntries.map((entry) => [String(entry.id || '').toLowerCase(), entry]));
    const selected = [];
    ids.forEach((id) => {
      const hit = byId.get(String(id).toLowerCase());
      if (hit) selected.push(hit);
    });
    return selected;
  }
  const safeLimit = Math.max(0, Number(limit) || 0);
  return safeLimit > 0 ? indexEntries.slice(0, safeLimit) : indexEntries.slice();
}

function buildRequests(site, query) {
  const requests = [];
  const siteUrl = resolveUrl(null, site);
  if (siteUrl) {
    requests.push({ key: 'site', url: siteUrl });
    SEARCH_TEMPLATES.forEach((template) => {
      requests.push({
        key: template
          .replace('{query}', '')
          .replace(/[/?=&]+/g, '-')
          .replace(/^-+|-+$/g, ''),
        url: resolveUrl(siteUrl, template.replace('{query}', encodeURIComponent(query))),
      });
    });
  }
  return requests.filter((it) => !!it.url);
}

function suggestExtension(contentType, key) {
  const type = String(contentType || '').toLowerCase();
  if (type.includes('json')) return 'json';
  if (type.includes('xml')) return 'xml';
  if (key === 'site') return 'html';
  return 'txt';
}

async function collectHttpFixtures({
  indexPath,
  outputDir,
  ids = [],
  limit = 20,
  query = 'love',
  timeoutMs = 15000,
  fetcher = fetch,
  pretty = true,
}) {
  const indexEntries = readJson(indexPath);
  if (!Array.isArray(indexEntries)) {
    throw new Error('Index must be an array');
  }

  const selected = selectPlugins(indexEntries, ids, limit);
  const summary = {
    generatedAt: new Date().toISOString(),
    indexPath,
    outputDir,
    query,
    timeoutMs,
    selectedCount: selected.length,
    ids: ids.length > 0 ? ids : null,
    plugins: [],
  };

  for (const entry of selected) {
    const pluginId = String(entry.id || '').trim();
    const pluginDir = path.join(outputDir, sanitizeSegment(pluginId));
    fs.mkdirSync(pluginDir, { recursive: true });

    const pluginSummary = {
      id: pluginId,
      name: String(entry.name || ''),
      lang: String(entry.lang || ''),
      site: String(entry.site || ''),
      fixtures: [],
    };

    const requests = buildRequests(entry.site, query);
    for (const request of requests) {
      const item = {
        key: request.key,
        url: request.url,
        status: null,
        ok: false,
        contentType: null,
        bytes: 0,
        file: null,
        error: null,
      };
      try {
        // eslint-disable-next-line no-await-in-loop
        const response = await fetchWithTimeout(request.url, fetcher, timeoutMs);
        item.status = response.status;
        item.ok = !!response.ok;
        item.contentType = response.headers?.get?.('content-type') || null;
        // eslint-disable-next-line no-await-in-loop
        const body = await response.text();
        const extension = suggestExtension(item.contentType, request.key);
        const fileName = `${sanitizeSegment(request.key)}.${extension}`;
        fs.writeFileSync(path.join(pluginDir, fileName), body, 'utf8');
        item.file = fileName;
        item.bytes = Buffer.byteLength(body, 'utf8');
      } catch (error) {
        item.error = String(error && error.message ? error.message : error);
      }
      pluginSummary.fixtures.push(item);
    }

    writeJson(path.join(pluginDir, 'manifest.json'), pluginSummary, true);
    summary.plugins.push(pluginSummary);
  }

  writeJson(path.join(outputDir, 'fixtures-summary.json'), summary, pretty);
  return summary;
}

async function main() {
  const parsed = parseArgs(process.argv.slice(2));
  if (parsed.help || !parsed.indexPath || !parsed.outputDir) {
    usage();
    process.exit(parsed.help ? 0 : 1);
  }
  const result = await collectHttpFixtures({
    indexPath: parsed.indexPath,
    outputDir: parsed.outputDir,
    ids: parsed.ids || [],
    limit: parsed.limit,
    query: parsed.query,
    timeoutMs: parsed.timeoutMs,
    pretty: parsed.pretty,
  });
  console.log(`Wrote fixtures for ${result.selectedCount} plugins to ${parsed.outputDir}`);
}

if (require.main === module) {
  main().catch((error) => {
    console.error(error);
    process.exit(1);
  });
}

module.exports = {
  SEARCH_TEMPLATES,
  parseArgs,
  selectPlugins,
  collectHttpFixtures,
};
