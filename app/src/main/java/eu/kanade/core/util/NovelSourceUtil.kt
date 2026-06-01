package eu.kanade.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun ifNovelSourcesLoaded(): Boolean {
    return remember { Injekt.get<NovelSourceManager>().isInitialized }.collectAsStateWithLifecycle().value
}
