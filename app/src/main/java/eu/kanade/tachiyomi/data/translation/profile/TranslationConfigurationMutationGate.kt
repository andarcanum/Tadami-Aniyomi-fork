package eu.kanade.tachiyomi.data.translation.profile

import kotlinx.coroutines.sync.Mutex

/** Serializes automatic imports with acknowledged UI configuration/credential edits, not HTTP reads. */
internal object TranslationConfigurationMutationGate {
    val mutex = Mutex()
}
