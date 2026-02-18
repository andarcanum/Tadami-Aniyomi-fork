#!/usr/bin/env node
const fs = require('node:fs');
const path = require('node:path');

const APP_MAIN_PREFIX = 'app/src/main/java/';
const DATA_MAIN_PREFIX = 'data/src/main/java/';
const DOMAIN_MAIN_PREFIX = 'domain/src/main/java/';

function normalizePath(filePath) {
  return String(filePath || '')
    .trim()
    .replace(/\\/g, '/')
    .replace(/^\.\//, '');
}

function hasAnySegment(value, segments) {
  return segments.some((segment) => value.includes(segment));
}

function isReadingCriticalPath(inputPath) {
  const filePath = normalizePath(inputPath);
  if (!filePath) return false;

  if (
    filePath === 'app/build.gradle.kts' ||
    filePath === 'gradle/libs.versions.toml' ||
    filePath === 'app/src/main/AndroidManifest.xml'
  ) {
    return true;
  }

  if (
    filePath.startsWith('source-api/') ||
    filePath.startsWith('source-local/') ||
    filePath.startsWith('app/src/main/assets/novel-plugin-') ||
    filePath.startsWith('app/src/main/assets/javascript/')
  ) {
    return true;
  }

  if (filePath.startsWith(APP_MAIN_PREFIX)) {
    return hasAnySegment(filePath, [
      '/ui/reader/',
      '/ui/player/',
      '/ui/webview/',
      '/ui/entries/anime/',
      '/ui/entries/manga/',
      '/ui/entries/novel/',
      '/ui/browse/anime/',
      '/ui/browse/manga/',
      '/ui/browse/novel/',
      '/data/download/anime/',
      '/data/download/manga/',
      '/source/anime/',
      '/source/manga/',
      '/source/novel/',
      '/animesource/',
      '/mangasource/',
      '/novelsource/',
      '/extension/anime/',
      '/extension/manga/',
      '/extension/novel/',
      '/presentation/reader/',
      '/presentation/player/',
      '/presentation/webview/',
      '/presentation/entries/anime/',
      '/presentation/entries/manga/',
      '/presentation/entries/novel/',
      '/presentation/browse/anime/',
      '/presentation/browse/manga/',
      '/presentation/browse/novel/',
    ]);
  }

  if (filePath.startsWith(DATA_MAIN_PREFIX)) {
    return hasAnySegment(filePath, [
      '/entries/anime/',
      '/entries/manga/',
      '/entries/novel/',
      '/items/chapter/',
      '/items/episode/',
      '/items/novelchapter/',
      '/source/anime/',
      '/source/manga/',
      '/source/novel/',
      '/track/anime/',
      '/track/manga/',
      '/track/novel/',
      '/extension/novel/',
    ]);
  }

  if (filePath.startsWith(DOMAIN_MAIN_PREFIX)) {
    return hasAnySegment(filePath, [
      '/entries/anime/',
      '/entries/manga/',
      '/entries/novel/',
      '/items/chapter/',
      '/items/episode/',
      '/items/novelchapter/',
      '/source/anime/',
      '/source/manga/',
      '/source/novel/',
      '/track/anime/',
      '/track/manga/',
      '/track/novel/',
    ]);
  }

  return false;
}

function classifyChangedPaths(paths) {
  const normalizedPaths = paths
    .map((item) => normalizePath(item))
    .filter((item) => item.length > 0);

  const matchedPaths = [];
  for (const filePath of normalizedPaths) {
    if (isReadingCriticalPath(filePath)) {
      matchedPaths.push(filePath);
    }
  }

  return {
    readingCritical: matchedPaths.length > 0,
    matchedPaths: [...new Set(matchedPaths)],
  };
}

function parseArgs(args) {
  const parsed = {};
  for (let i = 0; i < args.length; i += 1) {
    const arg = args[i];
    if (arg === '--input') {
      parsed.inputPath = args[i + 1];
      i += 1;
      continue;
    }
    if (arg === '--github-output') {
      parsed.githubOutputPath = args[i + 1];
      i += 1;
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

function readChangedPaths(inputPath) {
  if (inputPath) {
    return fs.readFileSync(path.resolve(inputPath), 'utf8').split(/\r?\n/);
  }
  return fs.readFileSync(0, 'utf8').split(/\r?\n/);
}

function writeGithubOutput(outputPath, result) {
  if (!outputPath) return;

  const delimiter = 'READING_CRITICAL_PATHS_EOF';
  const lines = [
    `reading_critical=${result.readingCritical}`,
    `matched_paths<<${delimiter}`,
    ...result.matchedPaths,
    delimiter,
    '',
  ];
  fs.appendFileSync(outputPath, lines.join('\n'), 'utf8');
}

function usage() {
  console.log(
    'Usage: node tools/ci/detect-reading-critical.js [--input <changed_files.txt>] [--github-output <output_file>]',
  );
}

function main() {
  const parsed = parseArgs(process.argv.slice(2));
  if (parsed.help) {
    usage();
    process.exit(0);
  }

  const changedPaths = readChangedPaths(parsed.inputPath);
  const result = classifyChangedPaths(changedPaths);
  const githubOutputPath = parsed.githubOutputPath || process.env.GITHUB_OUTPUT;

  writeGithubOutput(githubOutputPath, result);

  console.log(`reading_critical=${result.readingCritical}`);
  if (result.matchedPaths.length > 0) {
    console.log('matched_paths:');
    for (const filePath of result.matchedPaths) {
      console.log(`- ${filePath}`);
    }
  }
}

if (require.main === module) {
  main();
}

module.exports = {
  classifyChangedPaths,
  isReadingCriticalPath,
  normalizePath,
  parseArgs,
};
