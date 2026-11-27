package eu.kanade.presentation.easteregg.crystal

import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.easteregg.crystal.CrystalCampaignConditions
import eu.kanade.domain.easteregg.crystal.CrystalCampaignConfig
import eu.kanade.domain.easteregg.crystal.CrystalCampaignController
import eu.kanade.domain.easteregg.crystal.CrystalMemorialManager
import eu.kanade.domain.easteregg.crystal.CrystalRestoreState
import eu.kanade.domain.source.interactor.ForegroundIncognitoState
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.home.HomeScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.domain.easteregg.crystal.CrystalMemorialIds
import tachiyomi.domain.easteregg.crystal.CrystalMemorialState
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Readiness is shared by production presentation and the explicit debug progress check. */
@Composable
internal fun rememberCrystalCampaignConditions(
    migrationsReady: Boolean = true,
    startupSettled: Boolean = true,
    atRoot: Boolean = false,
    incognito: Boolean = false,
): CrystalCampaignConditions {
    val context = LocalContext.current.applicationContext
    val view = LocalView.current
    val policy = rememberCrystalMotionPolicy()
    val base = remember { Injekt.get<BasePreferences>() }
    val security = remember { Injekt.get<SecurityPreferences>() }
    val achievements = remember { Injekt.get<AchievementRepository>() }
    val onboardingComplete by base.shownOnboardingFlow().collectAsStateWithLifecycle()
    val globalPrivate by base.incognitoMode().collectAsStateWithLifecycle()
    val foregroundPrivate by ForegroundIncognitoState.active.collectAsStateWithLifecycle()
    val useAuthenticator by security.useAuthenticator().collectAsStateWithLifecycle()
    val restore by CrystalRestoreState.monitor.state.collectAsStateWithLifecycle()
    val restoreWork = remember(context) {
        BackupRestoreJob.observeActive(context).catch { error ->
            if (error is CancellationException) throw error
            logcat(LogPriority.WARN) { "Crystal restore-work readiness unavailable: " + error.javaClass.simpleName }
            emit(true) // Never present a gift if the queued-restore state is unknown.
        }
    }
    val restoreJobActive by restoreWork.collectAsStateWithLifecycle(initialValue = true)
    val catalogue = remember(achievements) {
        achievements.getAll().map { list ->
            list.any {
                it.id == CrystalMemorialIds.GRANT_KEY && it.type == AchievementType.EVENT &&
                    it.points == CrystalMemorialIds.XP &&
                    it.rewards.orEmpty().map { reward -> reward.id }.toSet() == CrystalMemorialIds.REWARDS
            }
        }.distinctUntilChanged().catch { error ->
            if (error is CancellationException) throw error
            logcat(LogPriority.WARN) { "Crystal catalogue readiness unavailable: " + error.javaClass.simpleName }
            emit(false)
        }
    }
    val catalogueReady by catalogue.collectAsStateWithLifecycle(initialValue = false)
    var focused by remember(view) { mutableStateOf(view.hasWindowFocus()) }
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { focused = it }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        onDispose {
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(listener)
        }
    }
    return CrystalCampaignConditions(
        migrationsReady, catalogueReady, policy.resumed,
        !useAuthenticator || !SecureActivityDelegate.requireUnlock,
        incognito || globalPrivate || foregroundPrivate,
        restore.inProgress || restoreJobActive,
        startupSettled && onboardingComplete, focused, atRoot, restore.completedRestores,
    )
}

/** Read synchronous privacy/restore state again after an IO snapshot, before persisting eligibility. */
internal fun CrystalCampaignConditions.withLivePrivacyAndRestore(): CrystalCampaignConditions {
    val restore = CrystalRestoreState.monitor.state.value
    return copy(
        incognito = incognito || ForegroundIncognitoState.active.value,
        restoring = restoring || restore.inProgress,
        restoreRevision = restore.completedRestores,
    )
}

/** Owns observation only while a safe foreground exists; root/focus is required to open a screen. */
@Composable
internal fun CrystalCampaignHost(
    navigator: Navigator,
    migrationsReady: Boolean,
    startupSettled: Boolean,
    incognito: Boolean,
) {
    val manager = remember { Injekt.get<CrystalMemorialManager>() }
    val saved by manager.state.collectAsStateWithLifecycle(initialValue = CrystalMemorialState())
    val conditions = rememberCrystalCampaignConditions(
        migrationsReady,
        startupSettled,
        navigator.lastItem == HomeScreen && navigator.items.size == 1,
        incognito,
    )
    val latestConditions by rememberUpdatedState(conditions)
    LaunchedEffect(conditions.canEvaluate, conditions.restoreRevision, saved.generation, saved.grantedAt) {
        if (conditions.canEvaluate && saved.isGranted) {
            try {
                manager.repository.repairProjections()
            } catch (
                e: CancellationException,
            ) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "Crystal keepsake repair deferred: " + e.javaClass.simpleName }
            }
        }
    }
    // Do not open media databases or observe titles until a real release is deliberately configured.
    if (!CrystalCampaignConfig.isProductionRelease || CrystalCampaignConfig.targetReleaseCodes.isEmpty()) return
    val controller by produceState<CrystalCampaignController?>(null) {
        try {
            value = withContext(Dispatchers.IO) { Injekt.get<CrystalCampaignController>() }
        } catch (
            e: CancellationException,
        ) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "Crystal campaign initialization deferred: " + e.javaClass.simpleName }
        }
    }
    val current = controller ?: return
    LaunchedEffect(current, conditions, saved.generation, saved.isEligible, saved.isGranted) {
        if (!conditions.canEvaluate) return@LaunchedEffect
        suspend fun evaluateAndPresent() {
            val evaluation = current.refresh(latestConditions.withLivePrivacyAndRestore()) {
                latestConditions.withLivePrivacyAndRestore()
            }
            val liveState = manager.repository.getState()
            val gate = latestConditions.withLivePrivacyAndRestore().copy(
                atRoot =
                navigator.lastItem == HomeScreen && navigator.items.size == 1,
            )
            if (current.claimPresentation(evaluation, liveState, gate)) navigator.push(CrystalMemorialScreen())
        }
        try {
            // Foreground/root/restore changes always re-query old flags, even if no new read event occurred.
            evaluateAndPresent()
            val liveState = manager.repository.getState()
            if (!liveState.isEligible && liveState.grantedAt == null) {
                current.progressChanges().collectLatest { evaluateAndPresent() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "Crystal eligibility check deferred: " + e.javaClass.simpleName }
        }
    }
}
