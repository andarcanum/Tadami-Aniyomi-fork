package eu.kanade.tachiyomi.ui.download.novel

import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.TabContent
import tachiyomi.core.common.i18n.stringResource as stringResourceCtx
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun Screen.novelDownloadTab(
    nestedScrollConnection: NestedScrollConnection,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val screenModel = rememberScreenModel { NovelDownloadQueueScreenModel() }
    val state by screenModel.state.collectAsState()

    return TabContent(
        titleRes = AYMR.strings.label_novel,
        searchEnabled = false,
        content = { contentPadding, _ ->
            NovelDownloadQueueScreen(
                contentPadding = contentPadding,
                state = state,
                onRefresh = screenModel::refreshStorage,
                onOpenFolder = {
                    val storageManager: StorageManager = Injekt.get()
                    val dir = storageManager.getDownloadsDirectory()?.createDirectory("novels")
                    if (dir == null) {
                        Toast.makeText(context, context.stringResourceCtx(AYMR.strings.download_folder_not_set), Toast.LENGTH_SHORT).show()
                        return@NovelDownloadQueueScreen
                    }
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(dir.uri, "resource/folder")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                            data = dir.uri
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(fallbackIntent)
                        } catch (_: Exception) {
                            Toast.makeText(context, context.stringResourceCtx(AYMR.strings.no_file_manager_found), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                nestedScrollConnection = nestedScrollConnection,
            )
        },
        numberTitle = state.queueCount,
        navigateUp = navigator::pop,
    )
}