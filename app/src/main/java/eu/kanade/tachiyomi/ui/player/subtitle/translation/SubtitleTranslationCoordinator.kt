package eu.kanade.tachiyomi.ui.player.subtitle.translation

class SubtitleTranslationCoordinator(
    private val providers: Map<SubtitleTranslationProviderId, SubtitleTranslationProvider>,
    private val cache: SubtitleTranslationDiskCache,
) {
    suspend fun translate(
        request: SubtitleTranslationRequest,
        onProgress: (SubtitleTranslationProgress) -> Unit = {},
    ): SubtitleTranslationResult {
        val provider = providers[request.providerId]
            ?: error("Subtitle translation provider is not available: ${request.providerId}")
        val translatableCues = request.document.cues.filter { it.text.isNotBlank() }
        onProgress(SubtitleTranslationProgress(0, translatableCues.size, SubtitleTranslationStage.CacheLookup))
        val cacheKey = buildSubtitleTranslationCacheKey(
            providerFingerprint = provider.fingerprint,
            sourceIdentity = request.sourceIdentity,
            sourceLanguage = request.sourceLanguage,
            targetLanguage = request.targetLanguage,
            document = request.document,
        )
        if (request.useCache) {
            cache.read(cacheKey)?.let { cached ->
                onProgress(
                    SubtitleTranslationProgress(cached.cues.size, cached.cues.size, SubtitleTranslationStage.Complete),
                )
                return SubtitleTranslationResult(
                    document = cached,
                    translatedCueCount = cached.cues.size,
                    cacheKey = cacheKey,
                    fromCache = true,
                )
            }
        }

        onProgress(SubtitleTranslationProgress(0, translatableCues.size, SubtitleTranslationStage.Translating))
        val providerResult = provider.translate(
            cues = translatableCues,
            sourceLanguage = request.sourceLanguage,
            targetLanguage = request.targetLanguage,
            onProgress = onProgress,
        )
        val translatedByIndex = when (providerResult) {
            is SubtitleTranslationProviderResult.Success -> providerResult.translatedByIndex
            is SubtitleTranslationProviderResult.Failure -> error(providerResult.message)
        }

        val translatedDocument = request.document.copy(
            cues = request.document.cues.map { cue ->
                translatedByIndex[cue.index]
                    ?.takeIf { it.isNotBlank() }
                    ?.let { cue.copy(text = it) }
                    ?: cue
            },
        )
        onProgress(
            SubtitleTranslationProgress(
                translated = translatedByIndex.size,
                total = translatableCues.size,
                stage = SubtitleTranslationStage.Writing,
            ),
        )
        if (request.useCache) {
            cache.write(cacheKey, translatedDocument)
        }
        onProgress(
            SubtitleTranslationProgress(
                translated = translatableCues.size,
                total = translatableCues.size,
                stage = SubtitleTranslationStage.Complete,
            ),
        )
        return SubtitleTranslationResult(
            document = translatedDocument,
            translatedCueCount = translatedByIndex.size,
            cacheKey = cacheKey,
            fromCache = false,
        )
    }
}
