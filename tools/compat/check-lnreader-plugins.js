#!/usr/bin/env node
const fs = require('node:fs');
const path = require('node:path');

const REQUIRED_FIELDS = ['id', 'name', 'site', 'lang', 'version', 'url'];

const SUPPORTED_MODULES = new Set([
  'cheerio',
  'htmlparser2',
  'dayjs',
  '@libs/novelStatus',
  '@libs/storage',
  '@libs/filterInputs',
  '@libs/defaultCover',
  '@libs/proseMirrorToHtml',
  '@libs/fetch',
  '@libs/isAbsoluteUrl',
  '@/types/constants',
  'urlencode',
]);

function usage() {
  const script = path.basename(process.argv[1] || 'check-lnreader-plugins.js');
  console.log(
    `Usage: ${script} --index <plugins.min.json> --plugins-dir <.js/plugins> --output <report.json> [--no-pretty]`,
  );
}

function parseArgs(args) {
  const parsed = {
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

function normalizeRel(value) {
  return value.replace(/\\/g, '/');
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
  if (!relativeFromUrl) {
    return { localPath: null, relativePath: null };
  }

  const candidates = new Set();
  candidates.add(normalizeRel(relativeFromUrl));
  try {
    candidates.add(normalizeRel(decodeURIComponent(relativeFromUrl)));
  } catch {
    // noop
  }

  for (const rel of candidates) {
    const hit = fileIndex.byRelative.get(rel);
    if (hit) return { localPath: hit, relativePath: rel };
  }

  for (const rel of candidates) {
    const parts = rel.split('/');
    if (parts.length < 2) continue;
    const lang = parts[0];
    const fileName = parts[parts.length - 1];
    const key = `${lang}/${fileName}`;
    const byLang = fileIndex.byLangAndName.get(key);
    if (byLang && byLang.length > 0) {
      return { localPath: byLang[0], relativePath: normalizeRel(path.relative(path.dirname(byLang[0]), byLang[0])) };
    }
  }

  return { localPath: null, relativePath: normalizeRel(relativeFromUrl) };
}

function findRequiredModules(scriptText) {
  const found = new Set();
  const regex = /require\((['"])(.+?)\1\)/g;
  let match = regex.exec(scriptText);
  while (match) {
    found.add(match[2]);
    match = regex.exec(scriptText);
  }
  return [...found].sort();
}

function hasDefaultExport(scriptText) {
  return /exports\.default\s*=/.test(scriptText);
}

function hasFetchProto(scriptText) {
  return /fetchProto/.test(scriptText);
}

function detectContractStages(scriptText) {
  const text = scriptText || '';
  const hasPopular = /popularNovels\s*[:=]|\bpopularNovels\s*\(/.test(text);
  const hasSearch = /searchNovels\s*[:=]|\bsearchNovels\s*\(/.test(text);
  const hasParseNovel = /parseNovel\s*[:=]|\bparseNovel\s*\(/.test(text);
  const hasParsePage = /parsePage\s*[:=]|\bparsePage\s*\(/.test(text);
  const hasParseChapter = /parseChapter\s*[:=]|\bparseChapter\s*\(/.test(text);

  const stages = {
    popular: hasPopular ? 'pass' : 'fail',
    search: hasSearch ? 'pass' : 'fail',
    novel: hasParseNovel ? 'pass' : 'fail',
    chapters: hasParseNovel || hasParsePage ? 'pass' : 'fail',
    chapterText: hasParseChapter ? 'pass' : 'fail',
  };

  const failedStages = Object.entries(stages)
    .filter(([, status]) => status !== 'pass')
    .map(([name]) => name);

  return { stages, failedStages };
}

function isMissingRequired(entry) {
  return REQUIRED_FIELDS.some((field) => {
    const value = entry[field];
    return value === undefined || value === null || String(value).trim() === '';
  });
}

function analyzeCompatibility({
  indexPath,
  pluginBaseDir,
  supportedModules = SUPPORTED_MODULES,
  supportsFetchProto = true,
}) {
  const indexRaw = readJson(indexPath);
  if (!Array.isArray(indexRaw)) {
    throw new Error('Index must be an array');
  }
  const entries = indexRaw;

  const fileIndex = buildPluginFileIndex(pluginBaseDir);
  const plugins = [];

  const entriesWithInvalidRequiredFields = [];
  const entriesMissingSha256 = [];
  const entriesWithCustomJs = [];
  const entriesWithCustomCss = [];

  const pluginsUsingFetchProto = [];
  const pluginsMissingDefaultExport = [];
  const pluginsMissingLocalScript = [];
  const unsupportedModulesByPlugin = {};
  const incompatibleIds = [];

  for (const entry of entries) {
    const id = String(entry.id ?? '');
    const issues = [];
    const missingRequired = isMissingRequired(entry);
    if (missingRequired) {
      entriesWithInvalidRequiredFields.push(id || '<missing-id>');
      issues.push({ code: 'invalid_required_fields', severity: 'critical' });
    }
    const sha256 = entry.sha256 == null ? '' : String(entry.sha256);
    if (sha256.trim() === '') {
      entriesMissingSha256.push(id || '<missing-id>');
      issues.push({ code: 'missing_sha256', severity: 'warning' });
    }
    if (entry.customJS && String(entry.customJS).trim() !== '') {
      entriesWithCustomJs.push(id || '<missing-id>');
    }
    if (entry.customCSS && String(entry.customCSS).trim() !== '') {
      entriesWithCustomCss.push(id || '<missing-id>');
    }

    let localScriptPath = null;
    let requiredModules = [];
    let unsupportedModules = [];
    let usesFetchProto = false;
    let defaultExport = false;
    let stages = {
      popular: 'fail',
      search: 'fail',
      novel: 'fail',
      chapters: 'fail',
      chapterText: 'fail',
    };
    let failedStages = ['popular', 'search', 'novel', 'chapters', 'chapterText'];

    const hasUrl = entry.url != null && String(entry.url).trim() !== '';
    if (hasUrl) {
      const resolved = resolveLocalScriptPath(String(entry.url), fileIndex);
      localScriptPath = resolved.localPath;
      if (!localScriptPath) {
        pluginsMissingLocalScript.push(id);
        issues.push({ code: 'script_not_found', severity: 'critical' });
      } else {
        const scriptText = fs.readFileSync(localScriptPath, 'utf8');
        requiredModules = findRequiredModules(scriptText);
        unsupportedModules = requiredModules.filter((name) => !supportedModules.has(name));
        if (unsupportedModules.length > 0) {
          unsupportedModulesByPlugin[id] = unsupportedModules;
          issues.push({ code: 'unsupported_modules', severity: 'critical', details: unsupportedModules });
        }
        usesFetchProto = hasFetchProto(scriptText);
        if (usesFetchProto) {
          pluginsUsingFetchProto.push(id);
          if (!supportsFetchProto) {
            issues.push({ code: 'uses_fetch_proto', severity: 'critical' });
          }
        }
        defaultExport = hasDefaultExport(scriptText);
        if (!defaultExport) {
          pluginsMissingDefaultExport.push(id);
          issues.push({ code: 'missing_default_export', severity: 'critical' });
        }

        const contract = detectContractStages(scriptText);
        stages = contract.stages;
        failedStages = contract.failedStages;
      }
    }

    const critical = issues.some((it) => it.severity === 'critical');
    if (critical) incompatibleIds.push(id || '<missing-id>');

    plugins.push({
      id,
      name: String(entry.name ?? ''),
      lang: String(entry.lang ?? ''),
      url: String(entry.url ?? ''),
      localScriptPath,
      requiredModules,
      unsupportedModules,
      usesFetchProto,
      hasDefaultExport: defaultExport,
      stages,
      failedStages,
      issues,
    });
  }

  const totalEntries = entries.length;
  const incompatibleSet = new Set(incompatibleIds);
  const summary = {
    totalEntries,
    entriesWithInvalidRequiredFields: entriesWithInvalidRequiredFields.length,
    entriesMissingSha256: entriesMissingSha256.length,
    entriesWithCustomJs: entriesWithCustomJs.length,
    entriesWithCustomCss: entriesWithCustomCss.length,
    resolvedScriptsMissingLocally: pluginsMissingLocalScript.length,
    pluginsWithUnsupportedModules: Object.keys(unsupportedModulesByPlugin).length,
    pluginsUsingFetchProto: pluginsUsingFetchProto.length,
    pluginsMissingDefaultExport: pluginsMissingDefaultExport.length,
    incompatiblePluginCount: incompatibleSet.size,
    compatiblePluginCount: totalEntries - incompatibleSet.size,
  };

  const stageFailures = {
    popular: 0,
    search: 0,
    novel: 0,
    chapters: 0,
    chapterText: 0,
  };
  let pluginsWithFailedStages = 0;
  for (const plugin of plugins) {
    if (plugin.failedStages.length > 0) {
      pluginsWithFailedStages += 1;
    }
    for (const stageName of plugin.failedStages) {
      if (stageFailures[stageName] != null) {
        stageFailures[stageName] += 1;
      }
    }
  }

  return {
    generatedAt: new Date().toISOString(),
    scenario: 'plugins.min.json',
    inputs: {
      indexPath,
      pluginBaseDir,
    },
    supportedModules: [...supportedModules].sort(),
    summary,
    contract: {
      pluginsWithFailedStages,
      stageFailures,
    },
    index: {
      entriesWithInvalidRequiredFields: entriesWithInvalidRequiredFields.sort(),
      entriesMissingSha256: entriesMissingSha256.sort(),
      entriesWithCustomJs: entriesWithCustomJs.sort(),
      entriesWithCustomCss: entriesWithCustomCss.sort(),
    },
    runtime: {
      pluginsMissingLocalScript: pluginsMissingLocalScript.sort(),
      unsupportedModulesByPlugin: Object.fromEntries(
        Object.entries(unsupportedModulesByPlugin)
          .sort(([a], [b]) => a.localeCompare(b))
          .map(([id, mods]) => [id, [...mods].sort()]),
      ),
      pluginsUsingFetchProto: pluginsUsingFetchProto.sort(),
      pluginsMissingDefaultExport: pluginsMissingDefaultExport.sort(),
    },
    incompatiblePluginIds: [...incompatibleSet].sort(),
    plugins,
  };
}

function writeJson(filePath, value, pretty = true) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  const json = pretty ? JSON.stringify(value, null, 2) : JSON.stringify(value);
  fs.writeFileSync(filePath, `${json}\n`, 'utf8');
}

function main() {
  const parsed = parseArgs(process.argv.slice(2));
  if (parsed.help || !parsed.indexPath || !parsed.pluginBaseDir || !parsed.outputPath) {
    usage();
    process.exit(parsed.help ? 0 : 1);
  }

  const report = analyzeCompatibility({
    indexPath: parsed.indexPath,
    pluginBaseDir: parsed.pluginBaseDir,
  });
  writeJson(parsed.outputPath, report, parsed.pretty);
  console.log(`Wrote ${parsed.outputPath}`);
}

if (require.main === module) {
  main();
}

module.exports = {
  REQUIRED_FIELDS,
  SUPPORTED_MODULES,
  analyzeCompatibility,
  parseArgs,
};
