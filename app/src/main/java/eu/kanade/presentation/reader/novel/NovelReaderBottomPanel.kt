package eu.kanade.presentation.reader.novel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsVoice
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import eu.kanade.domain.easteregg.lattice.LatticeCarrier
import eu.kanade.presentation.easteregg.lattice.LatticeCarrierSlot
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelReaderTtsUiState
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Bottom reader panel: TTS controls and the navigation/action row.
 *
 * Leaf composable extracted from [NovelReaderContentHost]. Takes narrow data plus grouped callbacks
 * instead of the whole reader state.
 */
@Composable
internal fun NovelReaderBottomPanel(
    visible: Boolean,
    ttsEnabled: Boolean,
    ttsUiState: NovelReaderTtsUiState,
    ttsStartRequest: Any?,
    previousChapterId: Long?,
    nextChapterId: Long?,
    chapterWebUrl: String?,
    chapterUrl: String?,
    novelUrl: String?,
    ttsPlacement: NovelReaderTtsSettingsPlacementSnapshot,
    geminiEnabled: Boolean,
    isGeminiTranslating: Boolean,
    geminiButtonActiveLabel: String,
    geminiButtonLabel: String,
    googleTranslationEnabled: Boolean,
    isGoogleTranslating: Boolean,
    hasGoogleTranslationCache: Boolean,
    isGoogleTranslationVisible: Boolean,
    dictionaryQuickAccessEnabled: Boolean,
    onOpenDictionaryHistory: (() -> Unit)?,
    onOpenPreviousChapter: ((Long) -> Unit)?,
    onOpenNextChapter: ((Long) -> Unit)?,
    onOpenChapterList: () -> Unit,
    onOpenWebView: (String) -> Unit,
    onScrollToTop: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTtsBehaviorSettings: () -> Unit,
    onOpenGeminiDialog: () -> Unit,
    onOpenGoogleDialog: () -> Unit,
    onToggleTtsPlayback: () -> Unit,
    onStopTtsPlayback: () -> Unit,
    onSkipPreviousTts: () -> Unit,
    onSkipNextTts: () -> Unit,
    onSetTtsEnginePackage: (String) -> Unit,
    onSetTtsVoiceId: (String) -> Unit,
    onSetTtsLocaleTag: (String) -> Unit,
    onSetTtsSpeechRate: (Float) -> Unit,
    onSetTtsPitch: (Float) -> Unit,
    onDisableTts: () -> Unit,
    onPreviewTtsVoice: (String) -> Unit,
    onStopTtsVoicePreview: () -> Unit,
    onOpenPreviousChapterFromReader: () -> Unit,
    onOpenNextChapterFromReader: () -> Unit,
    navigationBarHeightPx: Int,
    density: Density,
    modifier: Modifier = Modifier,
) {
    val panelSlideSpec = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val panelFadeSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val panelBackgroundColor = MaterialTheme.colorScheme
        .surfaceColorAtElevation(3.dp)
        .copy(alpha = if (isSystemInDarkTheme()) 0.9f else 0.95f)
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = panelSlideSpec,
        ) + fadeIn(animationSpec = panelFadeSpec),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = panelSlideSpec,
        ) + fadeOut(animationSpec = panelFadeSpec),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    panelBackgroundColor,
                    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                ),
        ) {
            if (ttsEnabled) {
                NovelReaderTtsControls(
                    uiState = ttsUiState,
                    onTogglePlayback = onToggleTtsPlayback,
                    onStop = onStopTtsPlayback,
                    onSkipPrevious = onSkipPreviousTts,
                    onSkipNext = onSkipNextTts,
                    onSetEnginePackage = onSetTtsEnginePackage,
                    onSetVoiceId = onSetTtsVoiceId,
                    onSetLocaleTag = onSetTtsLocaleTag,
                    onSetSpeechRate = onSetTtsSpeechRate,
                    onSetPitch = onSetTtsPitch,
                    onDisableTts = onDisableTts,
                    onPreviewVoice = onPreviewTtsVoice,
                    onStopVoicePreview = onStopTtsVoicePreview,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = MaterialTheme.padding.medium,
                            end = MaterialTheme.padding.medium,
                            top = MaterialTheme.padding.medium,
                        ),
                )

                HorizontalDivider(
                    modifier = Modifier.padding(top = MaterialTheme.padding.medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MaterialTheme.padding.medium,
                        vertical = MaterialTheme.padding.small,
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = onOpenPreviousChapterFromReader,
                    enabled = previousChapterId != null && onOpenPreviousChapter != null,
                ) {
                    Icon(imageVector = Icons.Outlined.ChevronLeft, contentDescription = null)
                }
                IconButton(onClick = onOpenChapterList) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ViewList,
                        contentDescription = stringResource(MR.strings.chapters),
                    )
                }
                // Only ever open a real web page. Local imports keep file names in these urls, and
                // feeding one to the WebView makes Chromium resolve it as a host
                // (net::ERR_NAME_NOT_RESOLVED), so bare paths hide the button entirely.
                val webPageUrl = chapterWebUrl?.takeIf { it.isNotBlank() }
                    ?: (chapterUrl?.takeIf { it.isNotBlank() } ?: novelUrl?.takeIf { it.isNotBlank() })
                        ?.takeIf { it.toHttpUrlOrNull() != null }
                if (webPageUrl != null) {
                    IconButton(onClick = { onOpenWebView(webPageUrl) }) {
                        Icon(imageVector = Icons.Filled.Public, contentDescription = null)
                    }
                }
                IconButton(onClick = onScrollToTop) {
                    Icon(imageVector = Icons.Filled.KeyboardArrowUp, contentDescription = null)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(imageVector = Icons.Outlined.Settings, contentDescription = null)
                }
                if (onOpenDictionaryHistory != null && dictionaryQuickAccessEnabled) {
                    IconButton(onClick = { onOpenDictionaryHistory() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = stringResource(
                                AYMR.strings.novel_reader_dictionary_history,
                            ),
                        )
                    }
                }
                LatticeCarrierSlot(LatticeCarrier.NOVEL)
                if (ttsPlacement.showFooterEntry) {
                    IconButton(onClick = onOpenTtsBehaviorSettings) {
                        Icon(
                            imageVector = Icons.Outlined.SettingsVoice,
                            contentDescription = stringResource(
                                AYMR.strings.novel_reader_tts_behavior_settings,
                            ),
                        )
                    }
                }
                if (geminiEnabled) {
                    IconButton(onClick = onOpenGeminiDialog) {
                        Text(
                            text = if (isGeminiTranslating) geminiButtonActiveLabel else geminiButtonLabel,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                if (googleTranslationEnabled) {
                    IconButton(onClick = onOpenGoogleDialog) {
                        Icon(
                            imageVector = Icons.Filled.Translate,
                            contentDescription = stringResource(AYMR.strings.novel_reader_google_translate),
                            tint = if (isGoogleTranslating ||
                                hasGoogleTranslationCache ||
                                isGoogleTranslationVisible
                            ) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            },
                        )
                    }
                }
                IconButton(
                    onClick = onOpenNextChapterFromReader,
                    enabled = nextChapterId != null && onOpenNextChapter != null,
                ) {
                    Icon(imageVector = Icons.Outlined.ChevronRight, contentDescription = null)
                }
            }
            Spacer(
                modifier = Modifier.padding(bottom = with(density) { navigationBarHeightPx.toDp() }),
            )
        }
    }
}
