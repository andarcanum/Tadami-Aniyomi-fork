#!/usr/bin/env node
const fs = require('node:fs');
const path = require('node:path');

function usage() {
  const script = path.basename(process.argv[1] || 'live-smoke-lnreader-plugins.js');
  console.log(
    `Usage: ${script} --index <plugins.min.json> --plugins-dir <.js/plugins> --output <report.json> [--query <text>] [--limit <n>] [--timeout-ms <n>] [--concurrency <n>]`,
  );
}

function parseArgs(args) {
  const parsed = {
    query: 'love',
    timeoutMs: 15000,
    concurrency: 8,
    limit: 0,
    pretty: true,
  };
  for (let i = 0; i < args.length; i += 1) {
    const arg = args[i];
    if (arg === '--index') {
      parsed.indexPath = args[i + 1];
      i += 1;
      continue;
    }
    if (arg === '--plugins-dir') {
      parsed.pluginBaseDir = args[i + 1];
      i += 1;
      continue;
    }
    if (arg === '--output') {
      parsed.outputPath = args[i + 1];
      i += 1;
      continue;
    }
    if (arg === '--query') {
      parsed.query = args[i + 1];
      i += 1;
      continue;
    }
    if (arg === '--limit') {
      parsed.limit = Number(args[i + 1] || 0);
      i += 1;
      continue;
    }
    if (arg === '--timeout-ms') {
      parsed.timeoutMs = Number(args[i + 1] || 15000);
      i += 1;
      continue;
    }
    if (arg === '--concurrency') {
      parsed.concurrency = Number(args[i + 1] || 8);
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

function normalizeRel(value) {
  return value.replace(/\\/g, '/');
}

function walkJsFiles(rootDir) {
  if (!fs.existsSync(rootDir)) return [];
  const out = [];
  const stack = [rootDir];
  while (stack.length > 0) {
    const current = stack.pop();
    const entries = fs.readdirSync(current, { withFileTypes: true });
    for (const entry of entries) {
      const fullPath = path.join(current, entry.name);
      if (entry.isDirectory()) {
        stack.push(fullPath);
      } else if (entry.isFile() && entry.name.endsWith('.js')) {
        out.push(fullPath);
      }
    }
  }
  return out;
}

function buildPluginFileIndex(pluginBaseDir) {
  const files = walkJsFiles(pluginBaseDir);
  const byRelative = new Map();
  const byLangAndName = new Map();
  for (const file of files) {
    const rel = normalizeRel(path.relative(pluginBaseDir, file));
    byRelative.set(rel, file);

    const parts = rel.split('/');
    const fileName = parts[parts.length - 1];
    const lang = parts.length >= 2 ? parts[0] : '';
    const key = `${lang}/${fileName}`;
    if (!byLangAndName.has(key)) byLangAndName.set(key, []);
    byLangAndName.get(key).push(file);
  }
  return { byRelative, byLangAndName };
}

function extractScriptRelativePathFromUrl(urlValue) {
  let pathname = '';
  try {
    const parsed = new URL(urlValue);
    pathname = parsed.pathname;
  } catch {
    pathname = urlValue;
  }
  const marker = '/.js/src/plugins/';
  const markerIdx = pathname.indexOf(marker);
  if (markerIdx < 0) return null;
  const tail = pathname.slice(markerIdx + marker.length);
  const clean = tail.split(/[?#]/, 1)[0];
  if (!clean) return null;
  return clean;
}

function resolveLocalScriptPath(urlValue, fileIndex) {
  const relativeFromUrl = extractScriptRelativePathFromUrl(urlValue);
  if (!relativeFromUrl) return null;

  const candidates = new Set();
  candidates.add(normalizeRel(relativeFromUrl));
  try {
    candidates.add(normalizeRel(decodeURIComponent(relativeFromUrl)));
  } catch {
    // noop
  }

  for (const rel of candidates) {
    const hit = fileIndex.byRelative.get(rel);
    if (hit) return hit;
  }

  for (const rel of candidates) {
    const parts = rel.split('/');
    if (parts.length < 2) continue;
    const lang = parts[0];
    const fileName = parts[parts.length - 1];
    const key = `${lang}/${fileName}`;
    const byLang = fileIndex.byLangAndName.get(key);
    if (byLang && byLang.length > 0) return byLang[0];
  }

  return null;
}

function hasFunction(scriptText, functionName) {
  const pattern = new RegExp(`${functionName}\\s*[:=]|\\b${functionName}\\s*\\(`);
  return pattern.test(scriptText || '');
}

function stagePass(extra = {}) {
  return { status: 'pass', ...extra };
}

function stageFail(code, extra = {}) {
  return { status: 'fail', code, ...extra };
}

function stageSkip(code, extra = {}) {
  return { status: 'skip', code, ...extra };
}

const SEARCH_ENDPOINT_TEMPLATES = [
  '/search/autocomplete?query={query}',
  '/search?query={query}',
  '/search?keyword={query}',
];

function classifyFetchError(error) {
  const message = String(error?.message || error || '').toLowerCase();
  if (message.includes('enotfound') || message.includes('eai_again') || message.includes('dns')) {
    return 'domain_unreachable';
  }
  if (message.includes('timeout') || message.includes('aborted')) {
    return 'request_timeout';
  }
  return 'network_error';
}

function resolveUrl(base, value) {
  const raw = String(value || '').trim();
  if (!raw) return null;
  try {
    return new URL(raw, base).toString();
  } catch {
    return null;
  }
}

function stripTags(text) {
  return String(text || '').replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
}

function extractNovelCandidates(searchJson) {
  const rawItems = Array.isArray(searchJson)
    ? searchJson
    : Array.isArray(searchJson?.items)
      ? searchJson.items
      : Array.isArray(searchJson?.data)
        ? searchJson.data
        : [];
  const out = [];
  for (const item of rawItems) {
    if (!item || typeof item !== 'object') continue;
    const rawPath = item.url || item.path || item.href || item.link;
    if (typeof rawPath !== 'string') continue;
    out.push(rawPath.trim());
  }
  return out.filter(Boolean);
}

function extractChapterUrls(html, baseUrl) {
  const out = [];
  const anchorRegex = /<a\b[^>]*href=(["'])(.*?)\1[^>]*>([\s\S]*?)<\/a>/gi;
  let match = anchorRegex.exec(html);
  while (match) {
    const href = String(match[2] || '').trim();
    const attrs = match[0];
    const text = stripTags(match[3] || '');
    const looksChapter = /chapter|glava|глава|capitulo|ch-\d|\/read/i.test(href)
      || /chapter|глава/i.test(attrs)
      || /chapter|глава/i.test(text);
    if (looksChapter) {
      const absolute = resolveUrl(baseUrl, href);
      if (absolute) out.push(absolute);
    }
    match = anchorRegex.exec(html);
  }
  return [...new Set(out)];
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

async function runLiveSmokeForPlugin({
  plugin,
  scriptText,
  fetcher = fetch,
  query = 'love',
  timeoutMs = 15000,
}) {
  const site = String(plugin.site || '').trim();

  const hasPopular = hasFunction(scriptText, 'popularNovels');
  const hasSearch = hasFunction(scriptText, 'searchNovels');
  const hasParseNovel = hasFunction(scriptText, 'parseNovel');
  const hasParseChapter = hasFunction(scriptText, 'parseChapter');

  const stages = {
    popular: hasPopular ? stageSkip('site_unavailable') : stageSkip('missing_handler'),
    search: hasSearch ? stageSkip('search_unavailable') : stageSkip('missing_handler'),
    novel: hasParseNovel ? stageSkip('search_unavailable') : stageSkip('missing_handler'),
    chapters: hasParseNovel ? stageSkip('novel_unavailable') : stageSkip('missing_handler'),
    chapterText: hasParseChapter ? stageSkip('chapters_unavailable') : stageSkip('missing_handler'),
  };

  let chapterCount = 0;
  let sampleChapterTextLength = 0;
  let novelUrl = null;
  let sampleChapterUrl = null;

  if (!site) {
    return {
      id: plugin.id,
      site,
      stages: {
        popular: stageFail('missing_site'),
        search: stageFail('missing_site'),
        novel: stageFail('missing_site'),
        chapters: stageFail('missing_site'),
        chapterText: stageFail('missing_site'),
      },
      chapterCount,
      sampleChapterTextLength,
      novelUrl,
      sampleChapterUrl,
    };
  }

  if (hasPopular) {
    try {
      const response = await fetchWithTimeout(site, fetcher, timeoutMs);
      if (!response.ok) {
        stages.popular = stageFail('request_failed', { httpStatus: response.status });
      } else {
        stages.popular = stagePass({ httpStatus: response.status });
      }
    } catch (error) {
      stages.popular = stageFail(classifyFetchError(error));
    }
  }

  let searchCandidates = [];
  if (hasSearch) {
    let searchFailure = stageFail('empty_search_results');
    for (const template of SEARCH_ENDPOINT_TEMPLATES) {
      const searchUrl = resolveUrl(
        site,
        template.replace('{query}', encodeURIComponent(query)),
      );
      if (!searchUrl) {
        searchFailure = stageFail('invalid_search_url');
        continue;
      }

      try {
        // eslint-disable-next-line no-await-in-loop
        const response = await fetchWithTimeout(searchUrl, fetcher, timeoutMs);
        if (!response.ok) {
          searchFailure = stageFail('request_failed', { httpStatus: response.status });
          continue;
        }

        // eslint-disable-next-line no-await-in-loop
        const payload = await response.json().catch(() => null);
        if (!payload) {
          searchFailure = stageFail('invalid_json');
          continue;
        }

        searchCandidates = extractNovelCandidates(payload);
        if (searchCandidates.length === 0) {
          searchFailure = stageFail('empty_search_results');
          continue;
        }

        stages.search = stagePass({ candidateCount: searchCandidates.length });
        break;
      } catch (error) {
        searchFailure = stageFail(classifyFetchError(error));
      }
    }

    if (searchCandidates.length === 0) {
      stages.search = searchFailure;
    }
  } else {
    stages.search = stageSkip('missing_handler');
  }

  if (hasParseNovel) {
    if (stages.search.status !== 'pass' || searchCandidates.length === 0) {
      stages.novel = stageSkip('search_unavailable');
      stages.chapters = stageSkip('novel_unavailable');
    } else {
      novelUrl = resolveUrl(site, searchCandidates[0]);
      if (!novelUrl) {
        stages.novel = stageFail('invalid_novel_url');
        stages.chapters = stageSkip('novel_unavailable');
      } else {
        try {
          const response = await fetchWithTimeout(novelUrl, fetcher, timeoutMs);
          if (!response.ok) {
            stages.novel = stageFail('request_failed', { httpStatus: response.status });
            stages.chapters = stageSkip('novel_unavailable');
          } else {
            stages.novel = stagePass({ httpStatus: response.status });
            const html = await response.text();
            const chapterUrls = extractChapterUrls(html, novelUrl);
            chapterCount = chapterUrls.length;
            if (chapterUrls.length === 0) {
              stages.chapters = stageFail('empty_chapters');
            } else {
              stages.chapters = stagePass({ chapterCount });
              sampleChapterUrl = chapterUrls[0];
            }
          }
        } catch (error) {
          const code = classifyFetchError(error);
          stages.novel = stageFail(code);
          stages.chapters = stageSkip('novel_unavailable');
        }
      }
    }
  }

  if (hasParseChapter) {
    if (stages.chapters.status !== 'pass' || !sampleChapterUrl) {
      stages.chapterText = stageSkip('chapters_unavailable');
    } else {
      try {
        const response = await fetchWithTimeout(sampleChapterUrl, fetcher, timeoutMs);
        if (!response.ok) {
          stages.chapterText = stageFail('request_failed', { httpStatus: response.status });
        } else {
          const html = await response.text();
          sampleChapterTextLength = stripTags(html).length;
          if (sampleChapterTextLength > 20) {
            stages.chapterText = stagePass({ sampleChapterTextLength });
          } else {
            stages.chapterText = stageFail('empty_chapter_text', { sampleChapterTextLength });
          }
        }
      } catch (error) {
        stages.chapterText = stageFail(classifyFetchError(error));
      }
    }
  }

  return {
    id: plugin.id,
    site,
    stages,
    chapterCount,
    sampleChapterTextLength,
    novelUrl,
    sampleChapterUrl,
  };
}

async function runLiveSmoke({
  indexPath,
  pluginBaseDir,
  query = 'love',
  timeoutMs = 15000,
  concurrency = 8,
  limit = 0,
  fetcher = fetch,
}) {
  const indexRaw = readJson(indexPath);
  if (!Array.isArray(indexRaw)) throw new Error('Index must be an array');
  const fileIndex = buildPluginFileIndex(pluginBaseDir);

  const entries = limit > 0 ? indexRaw.slice(0, limit) : indexRaw;
  const workers = Math.max(1, Number(concurrency) || 1);
  const plugins = new Array(entries.length);

  async function runSingle(index) {
    const entry = entries[index];
    const plugin = {
      id: String(entry.id || ''),
      site: String(entry.site || ''),
      url: String(entry.url || ''),
    };
    const localScriptPath = resolveLocalScriptPath(plugin.url, fileIndex);
    if (!localScriptPath) {
      plugins[index] = {
        id: plugin.id,
        site: plugin.site,
        stages: {
          popular: stageFail('script_not_found'),
          search: stageFail('script_not_found'),
          novel: stageFail('script_not_found'),
          chapters: stageFail('script_not_found'),
          chapterText: stageFail('script_not_found'),
        },
        chapterCount: 0,
        sampleChapterTextLength: 0,
        novelUrl: null,
        sampleChapterUrl: null,
      };
      return;
    }
    const scriptText = fs.readFileSync(localScriptPath, 'utf8');
    const result = await runLiveSmokeForPlugin({
      plugin,
      scriptText,
      query,
      timeoutMs,
      fetcher,
    });
    plugins[index] = result;
  }

  const queue = [];
  for (let cursor = 0; cursor < entries.length; cursor += 1) {
    queue.push(cursor);
  }

  async function workerLoop() {
    while (queue.length > 0) {
      const index = queue.shift();
      if (index == null) return;
      // eslint-disable-next-line no-await-in-loop
      await runSingle(index);
    }
  }

  await Promise.all(
    Array.from({ length: Math.min(workers, entries.length || 1) }, () => workerLoop()),
  );

  const stageFailures = {
    popular: 0,
    search: 0,
    novel: 0,
    chapters: 0,
    chapterText: 0,
  };
  const failureCodes = {};
  let passedAllStages = 0;
  let failedPlugins = 0;

  for (const plugin of plugins) {
    let hasFailure = false;
    const pluginFailureCodes = new Set();
    for (const [stageName, stage] of Object.entries(plugin.stages)) {
      if (stage.status === 'fail') {
        hasFailure = true;
        if (stageFailures[stageName] != null) {
          stageFailures[stageName] += 1;
        }
        if (stage.code) {
          pluginFailureCodes.add(stage.code);
        }
      }
    }
    for (const code of pluginFailureCodes) {
      failureCodes[code] = (failureCodes[code] || 0) + 1;
    }
    if (hasFailure) {
      failedPlugins += 1;
    } else {
      passedAllStages += 1;
    }
  }

  return {
    generatedAt: new Date().toISOString(),
    scenario: 'live-smoke',
    inputs: {
      indexPath,
      pluginBaseDir,
      query,
      timeoutMs,
      concurrency: workers,
      limit: limit > 0 ? limit : null,
    },
    summary: {
      totalPlugins: plugins.length,
      passedAllStages,
      failedPlugins,
    },
    stageFailures,
    failureCodes: Object.fromEntries(
      Object.entries(failureCodes).sort(([a], [b]) => a.localeCompare(b)),
    ),
    plugins,
  };
}

function writeJson(filePath, value, pretty = true) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  const json = pretty ? JSON.stringify(value, null, 2) : JSON.stringify(value);
  fs.writeFileSync(filePath, `${json}\n`, 'utf8');
}

async function main() {
  const parsed = parseArgs(process.argv.slice(2));
  if (parsed.help || !parsed.indexPath || !parsed.pluginBaseDir || !parsed.outputPath) {
    usage();
    process.exit(parsed.help ? 0 : 1);
  }

  const report = await runLiveSmoke({
    indexPath: parsed.indexPath,
    pluginBaseDir: parsed.pluginBaseDir,
    query: parsed.query,
    timeoutMs: parsed.timeoutMs,
    limit: parsed.limit,
  });
  writeJson(parsed.outputPath, report, parsed.pretty);
  console.log(`Wrote ${parsed.outputPath}`);
}

if (require.main === module) {
  main().catch((error) => {
    console.error(error);
    process.exit(1);
  });
}

module.exports = {
  classifyFetchError,
  runLiveSmokeForPlugin,
  runLiveSmoke,
  parseArgs,
};
