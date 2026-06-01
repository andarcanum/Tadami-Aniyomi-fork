package eu.kanade.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun ifAnimeSourcesLoaded(): Boolean {
    return remember { Injekt.get<AnimeSourceManager>().isInitialized }.collectAsStateWithLifecycle().value
}
