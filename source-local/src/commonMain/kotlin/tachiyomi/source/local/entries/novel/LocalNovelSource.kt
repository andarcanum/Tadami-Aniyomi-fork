package tachiyomi.source.local.entries.novel

import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.UnmeteredSource

expect class LocalNovelSource : NovelCatalogueSource, UnmeteredSource
