const test = require('node:test');
const assert = require('node:assert/strict');

const {
  buildOverrideCandidates,
} = require('./generate-override-candidates');

test('buildOverrideCandidates suggests chapter selector patch for empty chapters', () => {
  const liveSmoke = {
    plugins: [
      {
        id: 'rulate',
        stages: {
          chapters: { status: 'fail', code: 'empty_chapters' },
          chapterText: { status: 'fail', code: 'empty_chapters' },
        },
      },
    ],
  };

  const output = buildOverrideCandidates({ liveSmokeReport: liveSmoke });
  assert.equal(output.entries.length, 1);
  const candidate = output.entries[0];
  assert.equal(candidate.pluginId, 'rulate');
  assert.equal(candidate.chapterFallbackPolicy.fillMissingChapterNames, true);
  assert.ok(candidate.scriptPatches.length > 0);
  const chapterPatch = candidate.scriptPatches.find((patch) => patch.pattern === 'a.chapter');
  assert.ok(chapterPatch);
  assert.equal(
    chapterPatch.replacement,
    'a.chapter, a[href*=\\\"/chapter\\\"], a[href*=\\\"/read\\\"]',
  );
});

test('buildOverrideCandidates suggests domain aliases for unreachable domain', () => {
  const liveSmoke = {
    plugins: [
      {
        id: 'rulate',
        site: 'https://tl.rulate.ru',
        stages: {
          popular: { status: 'fail', code: 'domain_unreachable' },
          search: { status: 'fail', code: 'domain_unreachable' },
          novel: { status: 'fail', code: 'domain_unreachable' },
          chapters: { status: 'fail', code: 'domain_unreachable' },
          chapterText: { status: 'fail', code: 'domain_unreachable' },
        },
      },
    ],
  };

  const output = buildOverrideCandidates({ liveSmokeReport: liveSmoke });
  assert.equal(output.entries.length, 1);
  const candidate = output.entries[0];
  assert.equal(candidate.pluginId, 'rulate');
  assert.equal(candidate.domainAliases['https://rulate.ru'], 'https://tl.rulate.ru');
});

test('buildOverrideCandidates skips plugins that already exist in overrides', () => {
  const liveSmoke = {
    plugins: [
      {
        id: 'rulate',
        site: 'https://tl.rulate.ru',
        stages: {
          popular: { status: 'fail', code: 'domain_unreachable' },
        },
      },
    ],
  };
  const existingOverrides = {
    entries: [
      { pluginId: 'rulate' },
    ],
  };

  const output = buildOverrideCandidates({ liveSmokeReport: liveSmoke, existingOverrides });
  assert.equal(output.entries.length, 0);
});

test('buildOverrideCandidates keeps patch list unique', () => {
  const liveSmoke = {
    plugins: [
      {
        id: 'royalroad',
        site: 'https://www.royalroad.com',
        stages: {
          chapters: { status: 'fail', code: 'empty_chapters' },
          chapterText: { status: 'fail', code: 'empty_chapter_text' },
        },
      },
    ],
  };

  const output = buildOverrideCandidates({ liveSmokeReport: liveSmoke });
  assert.equal(output.entries.length, 1);

  const patches = output.entries[0].scriptPatches;
  const signatures = new Set(
    patches.map((patch) => `${patch.pattern}|${patch.replacement}|${patch.regex ? 1 : 0}|${patch.ignoreCase ? 1 : 0}`),
  );
  assert.equal(signatures.size, patches.length);
});
