package eu.kanade.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Aniview Premium Blurred Background System
 * Creates immersive full-screen backgrounds with blurred content images
 */
@Composable
fun ScreenWithBlurredBackground(
    backgroundImageModel: Any?, // Changed from String? - accepts EntryCover, String, or null
    modifier: Modifier = Modifier,
    blurRadius: Int = 60,
    dimAlpha: Float = 0.6f,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Layer 1: Blurred background image
        if (backgroundImageModel != null) {
            AsyncImage(
                model = backgroundImageModel, // Coil handles EntryCover and String
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blurRadius.dp)
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentScale = ContentScale.Crop,
                alpha = 0.4f,
            )
        } else {
            // Fallback: solid dark background
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F1116)),
            )
        }

        // Layer 2: Dark overlay for readability
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = dimAlpha)),
        )

        // Layer 3: Content
        content()
    }
}

/**
 * Simplified version without image - just gradient background
 */
@Composable
fun ScreenWithGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0F1116)),
        )

        content()
    }
}
