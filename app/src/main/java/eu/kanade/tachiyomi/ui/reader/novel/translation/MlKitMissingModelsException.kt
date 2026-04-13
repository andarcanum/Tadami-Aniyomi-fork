package eu.kanade.tachiyomi.ui.reader.novel.translation

class MlKitMissingModelsException(
    val missingLanguageCodes: Set<String>,
) : IllegalStateException(
    "Missing ML Kit translation model(s): ${missingLanguageCodes.joinToString(", ")}",
)
