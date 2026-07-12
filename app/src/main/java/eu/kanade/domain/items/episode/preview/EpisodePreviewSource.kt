package eu.kanade.domain.items.episode.preview

/**
 * Запрос на получение превью для серий одного аниме.
 * Remote id берутся из привязанных трекеров, когда они есть.
 */
data class PreviewRequest(
    val title: String,
    val episodeNumbers: List<Int>,
    val malId: Long? = null,
    val aniListId: Long? = null,
    val kitsuId: Long? = null,
    val simklId: Long? = null,
)

/**
 * Результат провайдера.
 *
 * @param previews номер серии -> URL реального превью этой серии.
 * @param fallbackPool кадры без привязки к номеру серии (например, скриншоты
 * Shikimori) — раздаются сериям, для которых точного превью не нашлось.
 */
data class EpisodePreviewResult(
    val previews: Map<Int, String> = emptyMap(),
    val fallbackPool: List<String> = emptyList(),
)

interface EpisodePreviewSource {
    val id: String

    suspend fun getPreviews(request: PreviewRequest): EpisodePreviewResult?
}
