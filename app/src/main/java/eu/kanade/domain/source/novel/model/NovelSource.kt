package eu.kanade.domain.source.novel.model

import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import tachiyomi.domain.source.novel.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

val Source.iconUrl: String?
    get() = Injekt.get<NovelExtensionManager>().getPluginIconUrlForSource(id)
