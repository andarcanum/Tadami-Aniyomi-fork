const test = require('node:test');
const assert = require('node:assert/strict');

const {
  classifyChangedPaths,
} = require('./detect-reading-critical');

test('marks reader and player paths as reading critical', () => {
  const result = classifyChangedPaths([
    'app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt',
    'app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt',
  ]);

  assert.equal(result.readingCritical, true);
  assert.deepEqual(
    result.matchedPaths,
    [
      'app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt',
      'app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt',
    ],
  );
});

test('marks manga/novel/anime browsing and entries paths as reading critical', () => {
  const result = classifyChangedPaths([
    'app/src/main/java/eu/kanade/tachiyomi/ui/entries/manga/MangaScreenModel.kt',
    'app/src/main/java/eu/kanade/tachiyomi/ui/entries/novel/NovelScreenModel.kt',
    'app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/source/browse/BrowseAnimeSourceScreen.kt',
  ]);

  assert.equal(result.readingCritical, true);
  assert.equal(result.matchedPaths.length, 3);
});

test('marks source/runtime/plugin paths as reading critical', () => {
  const result = classifyChangedPaths([
    'app/src/main/java/eu/kanade/tachiyomi/extension/novel/runtime/NovelJsSource.kt',
    'source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/Source.kt',
  ]);

  assert.equal(result.readingCritical, true);
  assert.equal(result.matchedPaths.length, 2);
});

test('marks presentation reader and entry paths as reading critical', () => {
  const result = classifyChangedPaths([
    'app/src/main/java/eu/kanade/presentation/reader/novel/NovelReaderScreen.kt',
    'app/src/main/java/eu/kanade/presentation/entries/anime/AnimeScreen.kt',
    'app/src/main/java/eu/kanade/presentation/browse/manga/BrowseMangaSourceScreen.kt',
  ]);

  assert.equal(result.readingCritical, true);
  assert.equal(result.matchedPaths.length, 3);
});

test('does not mark docs-only changes as reading critical', () => {
  const result = classifyChangedPaths([
    'README.md',
    'docs/reports/compat-data/nightly-lnreader-plugins-compat.json',
  ]);

  assert.equal(result.readingCritical, false);
  assert.deepEqual(result.matchedPaths, []);
});

test('normalizes windows paths before matching', () => {
  const result = classifyChangedPaths([
    'app\\src\\main\\java\\eu\\kanade\\tachiyomi\\ui\\webview\\WebViewActivity.kt',
  ]);

  assert.equal(result.readingCritical, true);
  assert.equal(result.matchedPaths[0], 'app/src/main/java/eu/kanade/tachiyomi/ui/webview/WebViewActivity.kt');
});
