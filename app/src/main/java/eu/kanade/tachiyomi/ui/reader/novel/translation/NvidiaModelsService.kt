package eu.kanade.tachiyomi.ui.reader.novel.translation

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class NvidiaModelsService(
    client: OkHttpClient,
    json: Json,
) : BaseOpenAiModelsService(client, json) {

    suspend fun fetchModels(baseUrl: String, apiKey: String): List<String> {
        return fetchModelIds(
            baseUrl = normalizeNvidiaBaseUrl(baseUrl),
            apiKey = apiKey,
        )
    }
}
