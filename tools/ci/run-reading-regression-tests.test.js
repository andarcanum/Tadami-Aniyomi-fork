const test = require('node:test');
const assert = require('node:assert/strict');

const {
  parseArgs,
  resolveGradleCommand,
  buildPrintableCommand,
  shouldUseShellForPlatform,
} = require('./run-reading-regression-tests');

test('parseArgs enables print-command flag', () => {
  const parsed = parseArgs(['--print-command']);
  assert.equal(parsed.printCommand, true);
});

test('resolveGradleCommand selects launcher by platform', () => {
  assert.ok(resolveGradleCommand('win32', 'C:/repo').replace(/\\/g, '/').endsWith('/gradlew.bat'));
  assert.ok(resolveGradleCommand('linux', '/repo').replace(/\\/g, '/').endsWith('/gradlew'));
  assert.ok(resolveGradleCommand('darwin', '/repo').replace(/\\/g, '/').endsWith('/gradlew'));
});

test('buildPrintableCommand includes gradle launcher and task', () => {
  const output = buildPrintableCommand('./gradlew', [':app:testReleaseUnitTest', '--tests', 'A.B.C']);
  assert.ok(output.startsWith('./gradlew :app:testReleaseUnitTest'));
  assert.ok(output.includes('--tests A.B.C'));
});

test('buildPrintableCommand quotes windows launcher paths with parentheses', () => {
  const output = buildPrintableCommand('D:\\repo\\ANIYOMI(ALL)\\gradlew.bat', [':app:testReleaseUnitTest']);
  assert.ok(output.startsWith('"D:\\repo\\ANIYOMI(ALL)\\gradlew.bat"'));
});

test('uses shell only on win32 launcher execution', () => {
  assert.equal(shouldUseShellForPlatform('win32'), true);
  assert.equal(shouldUseShellForPlatform('linux'), false);
});
