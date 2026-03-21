package tachiyomi.presentation.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tachiyomi.core.common.preference.Preference

@Composable
fun <T> Preference<T>.collectAsState(): State<T> {
    val flow = remember(this) { changes() }
    return flow.collectAsStateWithLifecycle(initialValue = get())
}
