#!/usr/bin/env node
const path = require('path');
const { convertRepo } = require('./converter');

function usage() {
  const script = path.basename(process.argv[1]);
  console.log(`Usage: ${script} --input <input.json> --output <repo.json> [--download-dir <dir>] [--no-pretty]`);
}

function parseArgs(args) {
  const parsed = {
    pretty: true,
  };

  for (let i = 0; i < args.length; i += 1) {
    const arg = args[i];
    if (arg === '--input') {
      parsed.inputPath = args[i + 1];
      i += 1;
      continue;
    }
    if (arg === '--output') {
      parsed.outputPath = args[i + 1];
      i += 1;
      continue;
    }
    if (arg === '--download-dir') {
      parsed.downloadDir = args[i + 1];
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
  }

  return parsed;
}

async function main() {
  const parsed = parseArgs(process.argv.slice(2));
  if (parsed.help || !parsed.inputPath || !parsed.outputPath) {
    usage();
    process.exit(parsed.help ? 0 : 1);
  }

  await convertRepo(parsed);
  console.log(`Wrote ${parsed.outputPath}`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
