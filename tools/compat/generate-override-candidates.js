#!/usr/bin/env node
const fs = require('node:fs');
const path = require('node:path');

function usage() {
  const script = path.basename(process.argv[1] || 'generate-override-candidates.js');
  console.log(
    `Usage: ${script} --live-smoke <report.json> --output <candidates.json> [--existing-overrides <novel-plugin-overrides.json>] [--no-pretty]`,
  );
}

function parseArgs(args) {
  const parsed = { pretty: true };
  for (let i = 0; i < args.length; i += 1) {
    const arg = args[i];
    if (arg === '--live-smoke') {
      parsed.liveSmokePath = args[i + 1];
      i += 1;
      continue;
    }
    if (arg === '--output') {
      parsed.outputPath = args[i + 1];
      i += 1;
      continue;
    }
    if (arg === '--existing-overrides') {
      parsed.existingOverridesPath = args[i + 1];
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

function mergeMap(base, extra) {
  const out = { ...(base || {}) };
  Object.entries(extra || {}).forEach(([key, value]) => {
    out[key] = value;
  });
  return out;
}

function makeDefaultChapterPatches() {
  return [
    {
      pattern: 'table > tbody > tr.chapter_row',
      replacement: 'table > tbody > tr.chapter_row, tr.chapter_row, .chapter_row',
    },
    {
      pattern: 'a.chapter',
      replacement: 'a.chapter, a[href*=\\\"/chapter\\\"], a[href*=\\\"/read\\\"]',
    },
  ];
}

function uniquePatches(patches) {
  const seen = new Set();
  const out = [];
  for (const patch of patches || []) {
    const key = `${patch.pattern}=>${patch.replacement}::${patch.regex ? 1 : 0}::${patch.ignoreCase ? 1 : 0}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(patch);
  }
  return out;
}

function extractFailureCodes(plugin) {
  const codes = [];
  for (const stage of Object.values(plugin.stages || {})) {
    if (stage && stage.status === 'fail' && stage.code) {
      codes.push(stage.code);
    }
  }
  return codes;
}

function buildDomainAliasCandidate(site) {
  if (!site || typeof site !== 'string') return {};
  let parsed;
  try {
    parsed = new URL(site);
  } catch {
    return {};
  }

  const aliases = {};
  const host = parsed.hostname.toLowerCase();
  const origin = `${parsed.protocol}//${host}`;

  if (host.startsWith('tl.')) {
    const root = host.slice(3);
    aliases[`${parsed.protocol}//${root}`] = origin;
    aliases[`${parsed.protocol}//www.${root}`] = origin;
  } else if (host.startsWith('www.')) {
    const root = host.slice(4);
    aliases[`${parsed.protocol}//${root}`] = origin;
  } else if (host.split('.').length >= 2) {
    aliases[`${parsed.protocol}//www.${host}`] = origin;
  }

  return aliases;
}

function buildOverrideCandidates({
  liveSmokeReport,
  existingOverrides = { entries: [] },
}) {
  const existingIds = new Set(
    (existingOverrides.entries || [])
      .map((entry) => String(entry.pluginId || '').toLowerCase())
      .filter(Boolean),
  );

  const entries = [];
  for (const plugin of liveSmokeReport.plugins || []) {
    const pluginId = String(plugin.id || '').trim();
    if (!pluginId) continue;
    if (existingIds.has(pluginId.toLowerCase())) continue;

    const codes = extractFailureCodes(plugin);
    const domainDown = codes.includes('domain_unreachable');
    const chapterIssues = codes.includes('empty_chapters')
      || codes.includes('empty_chapter_text')
      || codes.includes('missing_novel_candidate');

    if (!domainDown && !chapterIssues) continue;

    const candidate = {
      pluginId,
      domainAliases: {},
      scriptPatches: [],
      chapterFallbackPolicy: {
        fillMissingChapterNames: true,
        dropDuplicateChapterPaths: true,
        chapterNamePrefix: 'Chapter',
        stripFragmentFromChapterPath: true,
      },
      reasonCodes: [...new Set(codes)].sort(),
    };

    if (domainDown) {
      candidate.domainAliases = mergeMap(candidate.domainAliases, buildDomainAliasCandidate(plugin.site));
    }
    if (chapterIssues) {
      candidate.scriptPatches = uniquePatches([
        ...candidate.scriptPatches,
        ...makeDefaultChapterPatches(),
      ]);
    }

    if (Object.keys(candidate.domainAliases).length === 0 && candidate.scriptPatches.length === 0) {
      continue;
    }

    entries.push(candidate);
  }

  return {
    generatedAt: new Date().toISOString(),
    source: 'live-smoke',
    summary: {
      totalPlugins: (liveSmokeReport.plugins || []).length,
      candidateCount: entries.length,
      skippedExistingOverrides: existingIds.size,
    },
    entries,
  };
}

function main() {
  const parsed = parseArgs(process.argv.slice(2));
  if (parsed.help || !parsed.liveSmokePath || !parsed.outputPath) {
    usage();
    process.exit(parsed.help ? 0 : 1);
  }

  const liveSmokeReport = readJson(parsed.liveSmokePath);
  const existingOverrides = parsed.existingOverridesPath
    ? readJson(parsed.existingOverridesPath)
    : { entries: [] };

  const output = buildOverrideCandidates({
    liveSmokeReport,
    existingOverrides,
  });

  writeJson(parsed.outputPath, output, parsed.pretty);
  console.log(`Wrote ${parsed.outputPath}`);
}

if (require.main === module) {
  main();
}

module.exports = {
  buildOverrideCandidates,
  parseArgs,
};
