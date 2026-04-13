package eu.kanade.tachiyomi.ui.reader.novel.translation

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel

interface MlKitModelClient {
    suspend fun getDownloadedLanguageCodes(): Set<String>
    suspend fun download(languageCode: String)
    suspend fun delete(languageCode: String)
}

internal class RemoteMlKitModelClient(
    private val modelManager: RemoteModelManager = RemoteModelManager.getInstance(),
) : MlKitModelClient {

    override suspend fun getDownloadedLanguageCodes(): Set<String> {
        return modelManager
            .getDownloadedModels(TranslateRemoteModel::class.java)
            .await()
            .mapNotNull { model ->
                MlKitLanguageCatalog.canonicalizeLanguageCode(model.getLanguage())?.takeUnless {
                    MlKitLanguageCatalog.isBuiltInLanguageCode(it)
                }
            }
            .toSet()
    }

    override suspend fun download(languageCode: String) {
        if (MlKitLanguageCatalog.isBuiltInLanguageCode(languageCode)) return

        val model = TranslateRemoteModel.Builder(languageCode).build()
        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()
        modelManager.download(model, conditions).await()
    }

    override suspend fun delete(languageCode: String) {
        if (MlKitLanguageCatalog.isBuiltInLanguageCode(languageCode)) return

        val model = TranslateRemoteModel.Builder(languageCode).build()
        modelManager.deleteDownloadedModel(model).await()
    }
}
