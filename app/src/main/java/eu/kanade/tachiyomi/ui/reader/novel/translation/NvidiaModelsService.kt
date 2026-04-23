package eu.kanade.tachiyomi.ui.reader.novel.translation

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class NvidiaModelsService(
    client: OkHttpClient,
    json: Json,
) : BaseOpenAiModelsService(client, json) {

    suspend fun fetchModels(baseUrl: String, apiKey: String): List<String> {
        return fetchModelIds(
            baseUrl = baseUrl.trim().ifBlank { NVIDIA_BASE_URL },
            apiKey = apiKey,
        )
    }
}

private const val NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"
