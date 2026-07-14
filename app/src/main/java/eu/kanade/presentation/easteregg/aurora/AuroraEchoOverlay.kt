package eu.kanade.presentation.easteregg.aurora

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.kanade.domain.easteregg.aurora.AuroraEchoBus
import eu.kanade.domain.easteregg.aurora.AuroraLocalization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Разместить ОДИН раз в корне приложения, ПОСЛЕДНИМ ребёнком
 * корневого Box (поверх всего контента).
 *
 * Сам показывает:
 *  - кинематографичную вспышку на каждый пройденный этап (+ двойной
 *    хаптический пульс верного ответа);
 *  - одноразовые «шёпоты» (тихий намёк сверху, гаснет сам);
 *  - полноэкранный финальный экран награды при разблокировке.
 *
 * На неверные ответы не показывает ничего.
 */
@Composable
fun AuroraEchoOverlay() {
    val view = LocalView.current

    val manager = remember { Injekt.get<eu.kanade.domain.easteregg.aurora.AuroraHeartManager>() }
    val echo by AuroraEchoBus.flash.collectAsState()
    val presentation by manager.presentation.collectAsState()

    echo?.let {
        // Suppression: ensure no riddle dialog overlaps the cinematic flash (prevents race on solve emit)
        manager.dismissRiddle()
        LaunchedEffect(it) { AuroraSensory.solve(view) }
        AuroraEchoFlash(
            echoTitle = it.echoTitle ?: "Эхо Авроры",
            stageIndex = it.stageIndex,
            totalStages = it.totalStages,
            onFinished = {
                AuroraEchoBus.consume()
                manager.onFlashFinished(it)
            },
        )
    }

    // Шёпот: тихий полупрозрачный намёк, проявляется и гаснет сам.
    val whisper by AuroraEchoBus.whisper.collectAsState()
    whisper?.let { text ->
        val whisperAlpha = remember(text) { Animatable(0f) }
        LaunchedEffect(text) {
            whisperAlpha.animateTo(1f, tween(1600))
            delay(3400L)
            whisperAlpha.animateTo(0f, tween(1500))
            AuroraEchoBus.consumeWhisper()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(whisperAlpha.value),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = AuroraLocalization.translate(text).orEmpty(),
                color = Color(0x8CB8D8FF),
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 64.dp),
            )
        }
    }

    // Global seamless continuation using centralized manager presentation (follows strategist rec.)
    val managerState by manager.state.collectAsState() // hoisted
    // Guard: only show riddle dialog when no active flash (suppress overlap/race from bus emit + onFlash)
    if (presentation is eu.kanade.domain.easteregg.aurora.AuroraHeartManager.RiddlePresentation.Show && echo == null) {
        val p = presentation as eu.kanade.domain.easteregg.aurora.AuroraHeartManager.RiddlePresentation.Show
        val scope = rememberCoroutineScope()
        Dialog(
            onDismissRequest = { manager.dismissRiddle() },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AuroraRiddleScreen(
                riddle = manager.currentRiddle() ?: p.riddle,
                stageIndex = managerState.stageIndex,
                totalStages = managerState.totalStages,
                onPhrase = { phrase ->
                    scope.launch(Dispatchers.IO) {
                        val res = manager.offer(phrase)
                        if (res is eu.kanade.domain.easteregg.aurora.AuroraEcho.Progress) {
                            // Dismiss current riddle dialog so the cinematic flash is the pure focus.
                            // Bus already emitted inside offer(); flash onFinished will call onFlashFinished
                            // which sets presentation.Show for the *next* stage (seamless global continuation).
                            withContext(Dispatchers.Main) { manager.dismissRiddle() }
                        } else if (res is eu.kanade.domain.easteregg.aurora.AuroraEcho.Unlocked) {
                            withContext(Dispatchers.Main) { manager.dismissRiddle() }
                        }
                    }
                },
                onBack = { manager.dismissRiddle() },
            )
        }
    }

    val unlocked by AuroraEchoBus.unlocked.collectAsState()
    unlocked?.let { payload ->
        LaunchedEffect(payload) { AuroraSensory.solve(view) }
        Dialog(
            onDismissRequest = { AuroraEchoBus.consumeUnlocked() },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AuroraUnlockedScreen(
                payload = payload,
                onClose = { AuroraEchoBus.consumeUnlocked() },
            )
        }
    }
}
