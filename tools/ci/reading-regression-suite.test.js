const test = require('node:test');
const assert = require('node:assert/strict');

const {
  READING_REGRESSION_TESTS,
  buildGradleArgs,
} = require('./reading-regression-suite');

test('contains stable non-empty reading regression suite', () => {
  assert.ok(Array.isArray(READING_REGRESSION_TESTS));
  assert.ok(READING_REGRESSION_TESTS.length >= 10);
  assert.ok(READING_REGRESSION_TESTS.includes('eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModelTest'));
  assert.ok(READING_REGRESSION_TESTS.includes('eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceListingTest'));
});

test('buildGradleArgs expands --tests for each suite entry', () => {
  const args = buildGradleArgs();
  assert.equal(args[0], ':app:testReleaseUnitTest');
  assert.equal(args.filter((arg) => arg === '--tests').length, READING_REGRESSION_TESTS.length);
  assert.ok(args.includes('eu.kanade.tachiyomi.ui.entries.novel.NovelScreenModelErrorResolverTest'));
});

