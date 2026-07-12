package eu.kanade.domain.items.episode.preview

import kotlinx.serialization.Serializable
import java.net.URLEncoder

/**
 * Shikimori не отдаёт превью по конкретным сериям, но отдаёт реальные
 * скриншоты тайтла. Используем их как пул: серии без точного превью
 * получают кадры из пула по кругу. Id Shikimori совпадает с id MyAnimeList.
 */
class ShikimoriPreviewSource : BasePreviewSource() {

    override val id = "shikimori"

    override suspend fun getPreviews(request: PreviewRequest): EpisodePreviewResult? {
        val shikiId = request.malId ?: searchByTitle(request.title) ?: return null

        val body = getBody("$API/animes/$shikiId/screenshots") ?: return null
        val pool = json.decodeFromString<List<ShikiScreenshot>>(body)
            .mapNotNull { it.original }
            .map { if (it.startsWith("http")) it else BASE + it }

        return EpisodePreviewResult(fallbackPool = pool).takeIf { pool.isNotEmpty() }
    }

    private suspend fun searchByTitle(title: String): Long? {
        val query = URLEncoder.encode(title, "UTF-8")
        val body = getBody("$API/animes?search=$query&limit=1") ?: return null
        return json.decodeFromString<List<ShikiAnime>>(body).firstOrNull()?.id
    }

    @Serializable
    private data class ShikiAnime(val id: Long)

    @Serializable
    private data class ShikiScreenshot(val original: String? = null)

    companion object {
        private const val BASE = "https://shikimori.one"
        private const val API = "$BASE/api"
    }
}
