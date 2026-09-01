package eu.kanade.domain.entries

import eu.kanade.tachiyomi.data.database.models.anime.Episode
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter

/**
 * Pure gates for persisting the keepsake completion date of anime and novel entries
 * (mirror of `shouldRecordCompletion` for manga in eu.kanade.tachiyomi.ui.reader.model).
 * The date tracks the last witnessed end-read of the final episode/chapter of a completed
 * entry; bulk-marking from title screens never counts because only the reader/player
 * completion paths call these gates.
 */
fun shouldRecordAnimeCompletion(
    anime: Anime,
    episodes: List<Episode>,
    finishedEpisodeIsLast: Boolean,
): Boolean =
    finishedEpisodeIsLast &&
        anime.displayStatus == SManga.COMPLETED.toLong() &&
        episodes.isNotEmpty() &&
        episodes.all { it.seen }

fun shouldRecordNovelCompletion(
    novel: Novel,
    chapters: List<NovelChapter>,
    finishedChapterIsLast: Boolean,
): Boolean =
    finishedChapterIsLast &&
        novel.displayStatus == SManga.COMPLETED.toLong() &&
        chapters.isNotEmpty() &&
        chapters.all { it.read }
