package eu.kanade.presentation.entries.components

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import eu.kanade.domain.entries.metadata.TrackerMetadataDraft
import eu.kanade.domain.entries.metadata.TrackerMetadataFetchError
import eu.kanade.domain.entries.metadata.TrackerMetadataFetchOutcome
import eu.kanade.domain.entries.metadata.TrackerMetadataSource
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.applyAuroraSheetWindowFx
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

/**
 * Edit Info — variant E3: flat compact AdaptiveSheet form.
 * Single scroll column, uppercase section labels, status chips, tags + sticky footer.
 *
 * When [canFetchFromTracker] is true, shows an "Import from tracker" action that fills
 * the form from the linked tracker (does not auto-save).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditMetadataSheet(
    onDismissRequest: () -> Unit,
    currentTitle: String,
    currentAuthor: String?,
    currentArtist: String?,
    currentDescription: String?,
    currentGenre: List<String>?,
    currentStatus: Long?,
    hasArtist: Boolean,
    onSave: (
        title: String?,
        author: String?,
        artist: String?,
        description: String?,
        tags: List<String>?,
        status: Long?,
    ) -> Unit,
    onReset: () -> Unit,
    canFetchFromTracker: Boolean = false,
    onFetchFromTracker: (suspend (trackerId: Long?) -> TrackerMetadataFetchOutcome)? = null,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val context = LocalContext.current
    val accent = if (colors.isEInk) colors.textPrimary else colors.accent
    val supportsBlurBehind = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !colors.isEInk
    val scope = rememberCoroutineScope()
    var sheetReveal by remember { mutableFloatStateOf(0f) }

    var title by remember { mutableStateOf(currentTitle) }
    var author by remember { mutableStateOf(currentAuthor.orEmpty()) }
    var artist by remember { mutableStateOf(currentArtist.orEmpty()) }
    var description by remember { mutableStateOf(currentDescription.orEmpty()) }
    var tagsList by remember { mutableStateOf(currentGenre.orEmpty()) }
    var status by remember { mutableStateOf(currentStatus) }
    var showResetDialog by remember { mutableStateOf(false) }
    var newTagText by remember { mutableStateOf("") }
    var isFetchingFromTracker by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var fetchHint by remember { mutableStateOf<String?>(null) }
    var trackerPickerSources by remember { mutableStateOf<List<TrackerMetadataSource>?>(null) }

    fun applyDraft(draft: TrackerMetadataDraft) {
        draft.title?.let { title = it }
        draft.author?.let { author = it }
        draft.artist?.let { artist = it }
        draft.description?.let { description = it }
        draft.genres?.takeIf { it.isNotEmpty() }?.let { tagsList = it }
        draft.status?.let { status = it }
        fetchError = null
        fetchHint = context.stringResource(
            MR.strings.fetch_from_tracker_applied,
            draft.trackerName,
        )
    }

    fun errorMessage(error: TrackerMetadataFetchError): String = when (error) {
        TrackerMetadataFetchError.NoLinkedTracks ->
            context.stringResource(MR.strings.fetch_from_tracker_no_linked)
        TrackerMetadataFetchError.TrackerNotLoggedIn ->
            context.stringResource(MR.strings.fetch_from_tracker_not_logged_in)
        TrackerMetadataFetchError.NoSearchResults ->
            context.stringResource(MR.strings.fetch_from_tracker_no_results)
        TrackerMetadataFetchError.NoRemoteMatch ->
            context.stringResource(MR.strings.fetch_from_tracker_no_match)
        TrackerMetadataFetchError.EmptyMetadata ->
            context.stringResource(MR.strings.fetch_from_tracker_empty)
        is TrackerMetadataFetchError.Unexpected ->
            error.message?.takeIf { it.isNotBlank() }
                ?: context.stringResource(MR.strings.fetch_from_tracker_failed)
    }

    fun handleOutcome(outcome: TrackerMetadataFetchOutcome) {
        when (outcome) {
            is TrackerMetadataFetchOutcome.Success -> applyDraft(outcome.draft)
            is TrackerMetadataFetchOutcome.ChooseTracker -> {
                trackerPickerSources = outcome.sources
            }
            is TrackerMetadataFetchOutcome.Error -> {
                fetchError = errorMessage(outcome.error)
                fetchHint = null
            }
        }
    }

    fun runFetch(trackerId: Long?) {
        val fetch = onFetchFromTracker ?: return
        if (isFetchingFromTracker) return
        scope.launch {
            isFetchingFromTracker = true
            fetchError = null
            fetchHint = null
            try {
                handleOutcome(fetch(trackerId))
            } catch (e: Exception) {
                fetchError = e.message?.takeIf { it.isNotBlank() }
                    ?: context.stringResource(MR.strings.fetch_from_tracker_failed)
            } finally {
                isFetchingFromTracker = false
            }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        focusedBorderColor = accent.copy(alpha = 0.55f),
        unfocusedBorderColor = colors.textPrimary.copy(alpha = 0.18f),
        focusedLabelColor = accent,
        unfocusedLabelColor = colors.textSecondary,
        cursorColor = accent,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
    )
    val fieldShape = RoundedCornerShape(14.dp)

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = stringResource(MR.strings.action_reset_metadata),
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = stringResource(MR.strings.action_reset_metadata_confirmation),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onReset()
                        onDismissRequest()
                    },
                ) {
                    Text(
                        text = stringResource(MR.strings.action_reset_metadata),
                        color = accent,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(
                        text = stringResource(MR.strings.action_cancel),
                        color = colors.textSecondary,
                    )
                }
            },
            containerColor = colors.surface,
            shape = RoundedCornerShape(16.dp),
        )
    }

    trackerPickerSources?.let { sources ->
        AlertDialog(
            onDismissRequest = { trackerPickerSources = null },
            title = {
                Text(
                    text = stringResource(MR.strings.action_fetch_from_tracker_choose),
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sources.forEach { source ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    appHaptics.tap()
                                    trackerPickerSources = null
                                    runFetch(source.trackerId)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = source.trackerName,
                                color = colors.textPrimary,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (source.remoteTitle.isNotBlank()) {
                                Text(
                                    text = source.remoteTitle,
                                    color = colors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { trackerPickerSources = null }) {
                    Text(
                        text = stringResource(MR.strings.action_cancel),
                        color = colors.textSecondary,
                    )
                }
            },
            containerColor = colors.surface,
            shape = RoundedCornerShape(16.dp),
        )
    }

    AdaptiveSheet(
        onDismissRequest = onDismissRequest,
        containerColor = when {
            colors.isEInk -> MaterialTheme.colorScheme.surfaceContainerHigh
            !supportsBlurBehind -> colors.surface
            colors.isDark -> Color.Black.copy(alpha = 0.72f)
            else -> Color.White.copy(alpha = 0.90f)
        },
        scrimAlpha = if (supportsBlurBehind) 0f else 0.5f,
        onRevealChange = { sheetReveal = it },
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val revealState = rememberUpdatedState(sheetReveal)

        DisposableEffect(window, supportsBlurBehind) {
            val w = window
            if (w != null && supportsBlurBehind) {
                w.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
                w.setDimAmount(0f)
                w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                w.attributes = w.attributes.apply { blurBehindRadius = 0 }
            }
            onDispose {
                if (w != null && supportsBlurBehind) {
                    w.attributes = w.attributes.apply { blurBehindRadius = 0 }
                    w.setDimAmount(0f)
                    w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            }
        }

        LaunchedEffect(window, supportsBlurBehind) {
            val w = window ?: return@LaunchedEffect
            if (!supportsBlurBehind) return@LaunchedEffect
            snapshotFlow { revealState.value.coerceIn(0f, 1f) }
                .map { reveal -> (reveal * 20f).roundToInt().coerceIn(0, 20) }
                .distinctUntilChanged()
                .collect { step -> applyAuroraSheetWindowFx(w, step / 20f) }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(colors.textPrimary.copy(alpha = if (colors.isDark) 0.18f else 0.14f)),
            )

            Text(
                text = stringResource(MR.strings.action_edit_metadata),
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 2.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            EditMetaFieldLabel(stringResource(MR.strings.label_custom_title))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = fieldShape,
                colors = fieldColors,
            )

            EditMetaFieldLabel(stringResource(MR.strings.label_custom_author))
            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = fieldShape,
                colors = fieldColors,
            )

            if (hasArtist) {
                EditMetaFieldLabel(stringResource(MR.strings.label_custom_artist))
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = fieldShape,
                    colors = fieldColors,
                )
            }

            EditMetaFieldLabel(stringResource(MR.strings.label_custom_description))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
                shape = fieldShape,
                colors = fieldColors,
            )

            EditMetaFieldLabel(stringResource(MR.strings.label_custom_status))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Keep the most common statuses as chips for E3 density.
                val chipStatuses = listOf(
                    null to stringResource(MR.strings.status_default),
                    SManga.ONGOING.toLong() to stringResource(MR.strings.status_ongoing),
                    SManga.COMPLETED.toLong() to stringResource(MR.strings.status_completed),
                    SManga.ON_HIATUS.toLong() to stringResource(MR.strings.status_on_hiatus),
                    SManga.CANCELLED.toLong() to stringResource(MR.strings.status_cancelled),
                    SManga.PUBLISHING_FINISHED.toLong() to stringResource(MR.strings.status_publishing_finished),
                    SManga.LICENSED.toLong() to stringResource(MR.strings.status_licensed),
                    SManga.UNKNOWN.toLong() to stringResource(MR.strings.status_unknown),
                )
                chipStatuses.forEach { (value, label) ->
                    val selected = status == value
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    accent.copy(alpha = if (colors.isEInk) 0.18f else 0.16f)
                                } else {
                                    colors.textPrimary.copy(alpha = if (colors.isDark) 0.05f else 0.04f)
                                },
                            )
                            .border(
                                1.dp,
                                if (selected) {
                                    accent.copy(alpha = 0.45f)
                                } else {
                                    colors.textPrimary.copy(alpha = 0.12f)
                                },
                                CircleShape,
                            )
                            .clickable {
                                appHaptics.tap()
                                status = value
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = label,
                            color = if (selected) colors.textPrimary else colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    }
                }
            }

            EditMetaFieldLabel(stringResource(MR.strings.label_custom_genres))
            if (tagsList.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tagsList.forEach { tag ->
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    colors.textPrimary.copy(alpha = if (colors.isDark) 0.06f else 0.05f),
                                )
                                .border(1.dp, colors.textPrimary.copy(alpha = 0.10f), CircleShape)
                                .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = tag,
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        appHaptics.tap()
                                        tagsList = tagsList.filter { it != tag }
                                    },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newTagText,
                    onValueChange = { newTagText = it },
                    placeholder = {
                        Text(
                            text = stringResource(MR.strings.genre_input_placeholder),
                            color = colors.textSecondary,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = fieldShape,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val trimmed = newTagText.trim()
                            if (trimmed.isNotEmpty() && trimmed !in tagsList) {
                                tagsList = tagsList + trimmed
                                newTagText = ""
                            }
                        },
                    ),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent)
                        .clickable {
                            appHaptics.tap()
                            val trimmed = newTagText.trim()
                            if (trimmed.isNotEmpty() && trimmed !in tagsList) {
                                tagsList = tagsList + trimmed
                                newTagText = ""
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = stringResource(MR.strings.action_add),
                        color = if (colors.isEInk) colors.background else colors.textOnAccent,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            if (canFetchFromTracker && onFetchFromTracker != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            1.dp,
                            accent.copy(alpha = if (isFetchingFromTracker) 0.25f else 0.45f),
                            RoundedCornerShape(14.dp),
                        )
                        .clickable(enabled = !isFetchingFromTracker) {
                            appHaptics.tap()
                            runFetch(null)
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isFetchingFromTracker) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = accent,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = stringResource(MR.strings.action_fetch_from_tracker),
                            color = accent,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                fetchError?.let { message ->
                    Text(
                        text = message,
                        color = colors.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                fetchHint?.let { message ->
                    Text(
                        text = message,
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Footer: Reset · Cancel · Save
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(MR.strings.action_reset_metadata),
                    color = colors.error,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            appHaptics.tap()
                            showResetDialog = true
                        }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                )
                Text(
                    text = stringResource(MR.strings.action_cancel),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            appHaptics.tap()
                            onDismissRequest()
                        }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(accent)
                        .clickable {
                            appHaptics.tap()
                            val finalTitle = title.trim().takeIf { it.isNotBlank() }
                            val finalAuthor = author.trim().takeIf { it.isNotBlank() }
                            val finalArtist = artist.trim().takeIf { it.isNotBlank() }
                            val finalDescription = description.trim().takeIf { it.isNotBlank() }
                            onSave(
                                finalTitle,
                                finalAuthor,
                                finalArtist,
                                finalDescription,
                                tagsList,
                                status,
                            )
                            onDismissRequest()
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(MR.strings.action_save),
                        color = if (colors.isEInk) colors.background else colors.textOnAccent,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun EditMetaFieldLabel(text: String) {
    val colors = AuroraTheme.colors
    val accent = if (colors.isEInk) colors.textPrimary else colors.accent
    Text(
        text = text.uppercase(),
        color = accent.copy(alpha = if (colors.isEInk) 1f else 0.85f),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.0.sp,
        modifier = Modifier.padding(top = 2.dp, start = 2.dp),
    )
}
