#!/usr/bin/env node
const fs = require('node:fs');
const path = require('node:path');

const REQUIRED_ITEMS = [
  'I ran manual smoke checks for anime, manga, and novel viewing flows.',
  'I verified there were no crashes, ANRs, or blank screens in those flows.',
];

function normalizeText(value) {
  return String(value || '').trim();
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function buildCheckedPattern(itemText) {
  return new RegExp(`-\\s*\\[[xX]\\]\\s*${escapeRegex(itemText)}`, 'm');
}

function validateReadingSmokeChecklist(prBody) {
  const body = normalizeText(prBody);
  const missingItems = [];

  for (const item of REQUIRED_ITEMS) {
    const pattern = buildCheckedPattern(item);
    if (!pattern.test(body)) {
      missingItems.push(`- [x] ${item}`);
    }
  }

  return {
    ok: missingItems.length === 0,
    missingItems,
  };
}

function parseArgs(args) {
  const parsed = {};
  for (let i = 0; i < args.length; i += 1) {
    const arg = args[i];
    if (arg === '--pr-body-file') {
      parsed.prBodyFile = args[i + 1];
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

function usage() {
  console.log(
    'Usage: node tools/ci/validate-reading-smoke.js [--pr-body-file <path>] (or provide PR body through stdin)',
  );
}

function readPrBody(prBodyFile) {
  if (prBodyFile) {
    return fs.readFileSync(path.resolve(prBodyFile), 'utf8');
  }
  return fs.readFileSync(0, 'utf8');
}

function main() {
  const parsed = parseArgs(process.argv.slice(2));
  if (parsed.help) {
    usage();
    process.exit(0);
  }

  const prBody = readPrBody(parsed.prBodyFile);
  const result = validateReadingSmokeChecklist(prBody);

  if (result.ok) {
    console.log('Reading smoke checklist validated.');
    return;
  }

  console.error('Missing required checked items in PR body:');
  for (const item of result.missingItems) {
    console.error(item);
  }
  process.exit(1);
}

if (require.main === module) {
  main();
}

module.exports = {
  REQUIRED_ITEMS,
  validateReadingSmokeChecklist,
};

