const READING_REGRESSION_TESTS = [
  'eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferencesTest',
  'eu.kanade.tachiyomi.ui.reader.MangaReaderProgressCodecTest',
  'eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferencesTest',
  'eu.kanade.tachiyomi.ui.reader.novel.NovelReaderUrlResolverTest',
  'eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModelTest',
  'eu.kanade.presentation.reader.novel.NovelReaderUiVisibilityTest',
  'eu.kanade.tachiyomi.ui.entries.manga.MangaScreenUrlNormalizationTest',
  'eu.kanade.tachiyomi.ui.entries.manga.MangaScreenModelScanlatorSelectionTest',
  'eu.kanade.tachiyomi.ui.entries.novel.NovelScreenModelTest',
  'eu.kanade.tachiyomi.ui.entries.novel.NovelScreenUrlResolverTest',
  'eu.kanade.tachiyomi.ui.entries.novel.NovelScreenModelErrorResolverTest',
  'eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceListingTest',
  'eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceListingTest',
  'eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourceListingTest',
  'eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourceScreenModelTest',
  'eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourceWebUrlResolverTest',
];

function buildGradleArgs() {
  const args = [':app:testReleaseUnitTest'];
  for (const testClass of READING_REGRESSION_TESTS) {
    args.push('--tests', testClass);
  }
  return args;
}

module.exports = {
  READING_REGRESSION_TESTS,
  buildGradleArgs,
};

