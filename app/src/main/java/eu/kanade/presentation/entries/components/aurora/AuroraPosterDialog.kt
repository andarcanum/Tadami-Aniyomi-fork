package eu.kanade.presentation.entries.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tachiyomi.presentation.core.components.material.Scaffold

@Composable
internal fun AuroraPosterDialog(
    snackbarHostState: SnackbarHostState,
    onDismissRequest: () -> Unit,
    bottomBar: @Composable (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = androidx.compose.ui.Modifier
                .fillMaxSize()
                .background(Color(0xFF050505)),
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                containerColor = Color.Transparent,
                bottomBar = { bottomBar?.invoke() },
            ) { contentPadding ->
                content(contentPadding)
            }
        }
    }
}
