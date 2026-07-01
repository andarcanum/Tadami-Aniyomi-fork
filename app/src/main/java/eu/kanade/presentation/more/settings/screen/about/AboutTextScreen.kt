package eu.kanade.presentation.more.settings.screen.about

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.halilibo.richtext.ui.string.RichTextStringStyle
import eu.kanade.presentation.more.settings.SettingsScaffold
import eu.kanade.presentation.more.settings.canScroll
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.util.Screen
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

class AboutTextScreen(
    private val title: String,
    private val content: String,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val uiStyle = rememberResolvedSettingsUiStyle()
        val scrollState = rememberScrollState()

        val formattedContent = remember(content) {
            stripFirstHeader(content)
        }

        val context = LocalContext.current
        val originalUriHandler = LocalUriHandler.current
        val customUriHandler = remember(navigator, originalUriHandler, context) {
            object : androidx.compose.ui.platform.UriHandler {
                override fun openUri(uri: String) {
                    if (uri == "app://dmca" || uri == "dmca" || uri == "dmca.md" || uri.endsWith("DMCA.md")) {
                        val dmcaTitle = context.stringResource(MR.strings.pref_copyright_dmca)
                        val dmcaContent = context.stringResource(MR.strings.pref_copyright_dmca_text)
                        navigator.push(AboutTextScreen(title = dmcaTitle, content = dmcaContent))
                    } else {
                        originalUriHandler.openUri(uri)
                    }
                }
            }
        }

        // Custom Typography to calibrate all line heights and font sizes, preventing text overlapping
        val baseTypography = MaterialTheme.typography
        val customTypography = remember(baseTypography) {
            baseTypography.copy(
                displayLarge = baseTypography.displayLarge.copy(
                    fontSize = 26.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
                displayMedium = baseTypography.displayMedium.copy(
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                ),
                displaySmall = baseTypography.displaySmall.copy(
                    fontSize = 22.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
                ),
                headlineLarge = baseTypography.headlineLarge.copy(
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                ),
                headlineMedium = baseTypography.headlineMedium.copy(
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                headlineSmall = baseTypography.headlineSmall.copy(
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                titleLarge = baseTypography.titleLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                titleMedium = baseTypography.titleMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium,
                ),
                titleSmall = baseTypography.titleSmall.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                ),
                bodyLarge = baseTypography.bodyLarge.copy(
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                ),
                bodyMedium = baseTypography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                ),
                bodySmall = baseTypography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                ),
            )
        }

        var animTriggered by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            animTriggered = true
        }

        val alpha by animateFloatAsState(
            targetValue = if (animTriggered) 1f else 0f,
            animationSpec = tween(600, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
            label = "alpha",
        )
        val translationY by animateFloatAsState(
            targetValue = if (animTriggered) 0f else 30f,
            animationSpec = tween(600, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
            label = "translationY",
        )

        SettingsScaffold(
            title = title,
            uiStyle = uiStyle,
            onBackPressed = navigator::pop,
            topBarCanScroll = { scrollState.canScroll() },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.alpha = alpha
                        this.translationY = translationY.dp.toPx()
                    }
                    .verticalScroll(scrollState)
                    .padding(contentPadding)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
            ) {
                MaterialTheme(typography = customTypography) {
                    CompositionLocalProvider(LocalUriHandler provides customUriHandler) {
                        RichText(
                            style = RichTextStyle(
                                stringStyle = RichTextStringStyle(
                                    linkStyle = SpanStyle(color = MaterialTheme.colorScheme.primary),
                                ),
                            ),
                        ) {
                            Markdown(content = formattedContent)
                        }
                    }
                }
            }
        }
    }

    private fun stripFirstHeader(content: String): String {
        val lines = content.lines()
        if (lines.isNotEmpty() && lines.first().trimStart().startsWith("#")) {
            // Skip the first line and any empty lines following it
            return lines.drop(1).dropWhile { it.isBlank() }.joinToString("\n")
        }
        return content
    }
}
