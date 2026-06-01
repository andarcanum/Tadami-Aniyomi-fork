package eu.kanade.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun ifMangaSourcesLoaded(): Boolean {
    return remember { Injekt.get<MangaSourceManager>().isInitialized }.collectAsStateWithLifecycle().value
}
