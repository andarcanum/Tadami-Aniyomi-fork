#!/usr/bin/env node
const { spawnSync } = require('node:child_process');
const path = require('node:path');

const { buildGradleArgs } = require('./reading-regression-suite');

function parseArgs(args) {
  const parsed = {};
  for (const arg of args) {
    if (arg === '--print-command') {
      parsed.printCommand = true;
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
  console.log('Usage: node tools/ci/run-reading-regression-tests.js [--print-command]');
}

function resolveGradleCommand(platform = process.platform, cwd = process.cwd()) {
  const launcher = platform === 'win32' ? 'gradlew.bat' : 'gradlew';
  return path.resolve(cwd, launcher);
}

function shouldUseShellForPlatform(platform = process.platform) {
  return platform === 'win32';
}

function buildPrintableCommand(command, args) {
  const needsQuote = (token) => /[\s()&]/.test(token);
  return [command, ...args]
    .map((token) => (needsQuote(token) ? `"${token}"` : token))
    .join(' ');
}

function main() {
  const parsed = parseArgs(process.argv.slice(2));
  if (parsed.help) {
    usage();
    process.exit(0);
  }

  const gradleCommand = resolveGradleCommand(process.platform, process.cwd());
  const args = buildGradleArgs();
  const command = buildPrintableCommand(gradleCommand, args);
  const useShell = shouldUseShellForPlatform();

  if (parsed.printCommand) {
    console.log(command);
    return;
  }

  const run = useShell
    ? spawnSync(command, {
        stdio: 'inherit',
        shell: true,
      })
    : spawnSync(gradleCommand, args, {
        stdio: 'inherit',
        shell: false,
      });
  if (run.error) {
    console.error(run.error.message);
    process.exit(1);
  }
  if (run.status !== 0) {
    process.exit(run.status || 1);
  }
}

if (require.main === module) {
  main();
}

module.exports = {
  buildPrintableCommand,
  parseArgs,
  resolveGradleCommand,
  shouldUseShellForPlatform,
};
