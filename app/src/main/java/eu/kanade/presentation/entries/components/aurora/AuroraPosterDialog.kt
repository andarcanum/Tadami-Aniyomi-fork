package eu.kanade.presentation.entries.components.aurora

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
        var animateTrigger by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            animateTrigger = true
        }

        val scale by animateFloatAsState(
            targetValue = if (animateTrigger) 1f else 0.95f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow,
            ),
            label = "scale",
        )
        val alpha by animateFloatAsState(
            targetValue = if (animateTrigger) 1f else 0f,
            animationSpec = tween(300),
            label = "alpha",
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF050505))
                .graphicsLayer {
                    this.scaleX = scale
                    this.scaleY = scale
                    this.alpha = alpha
                },
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
