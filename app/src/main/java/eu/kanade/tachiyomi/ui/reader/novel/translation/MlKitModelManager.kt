package eu.kanade.tachiyomi.ui.reader.novel.translation

import java.util.Locale

data class MlKitTranslationModelInfo(
    val languageCode: String,
    val displayName: String,
    val isDownloaded: Boolean,
    val isBuiltIn: Boolean,
) {
    val canDownload: Boolean
        get() = !isBuiltIn && !isDownloaded

    val canDelete: Boolean
        get() = !isBuiltIn && isDownloaded
}

class MlKitModelManager(
    private val client: MlKitModelClient = RemoteMlKitModelClient(),
) {

    suspend fun listModels(
        supportedLanguageCodes: List<String> = MlKitLanguageCatalog.supportedLanguageCodes(),
        locale: Locale = Locale.getDefault(),
    ): List<MlKitTranslationModelInfo> {
        val downloadedLanguageCodes = downloadedLanguageCodes()
        return MlKitLanguageCatalog.supportedLanguageOptions(
            languageCodes = supportedLanguageCodes,
            locale = locale,
        ).map { option ->
            MlKitTranslationModelInfo(
                languageCode = option.languageCode,
                displayName = option.displayName,
                isDownloaded = option.languageCode in downloadedLanguageCodes,
                isBuiltIn = option.isBuiltIn,
            )
        }
    }

    suspend fun downloadedLanguageCodes(): Set<String> {
        return client
            .getDownloadedLanguageCodes()
            .mapNotNull { languageCode ->
                MlKitLanguageCatalog.canonicalizeLanguageCode(languageCode)
            }
            .filterNot { languageCode ->
                MlKitLanguageCatalog.isBuiltInLanguageCode(languageCode)
            }
            .toSet()
    }

    suspend fun missingDownloadedLanguageCodes(requiredLanguageCodes: Set<String>): Set<String> {
        val downloaded = downloadedLanguageCodes()
        return requiredLanguageCodes
            .filterNot { languageCode ->
                MlKitLanguageCatalog.isBuiltInLanguageCode(languageCode) ||
                    downloaded.contains(languageCode)
            }
            .toSet()
    }

    suspend fun isDownloaded(languageCode: String): Boolean {
        val canonicalLanguageCode = canonicalizeLanguageCodeOrThrow(languageCode)
        if (MlKitLanguageCatalog.isBuiltInLanguageCode(canonicalLanguageCode)) return true
        return downloadedLanguageCodes().contains(canonicalLanguageCode)
    }

    suspend fun download(languageCode: String) {
        val canonicalLanguageCode = canonicalizeLanguageCodeOrThrow(languageCode)
        if (MlKitLanguageCatalog.isBuiltInLanguageCode(canonicalLanguageCode)) return
        client.download(canonicalLanguageCode)
    }

    suspend fun delete(languageCode: String) {
        val canonicalLanguageCode = canonicalizeLanguageCodeOrThrow(languageCode)
        if (MlKitLanguageCatalog.isBuiltInLanguageCode(canonicalLanguageCode)) return
        client.delete(canonicalLanguageCode)
    }

    suspend fun deleteAll() {
        downloadedLanguageCodes().forEach { languageCode ->
            client.delete(languageCode)
        }
    }

    fun requiredModelLanguageCodes(
        sourceLanguage: String,
        targetLanguage: String,
    ): Set<String> {
        return MlKitLanguageCatalog.requiredModelLanguageCodes(
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
        )
    }

    private fun canonicalizeLanguageCodeOrThrow(languageCode: String): String {
        return MlKitLanguageCatalog.canonicalizeLanguageCode(languageCode)
            ?: throw IllegalArgumentException("Unsupported ML Kit language code: $languageCode")
    }
}
