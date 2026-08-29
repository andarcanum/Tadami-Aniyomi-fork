package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Small loading indicator shown at the bottom of the feed while the next page is fetched.
 */
@Composable
fun ReelsNextPageLoader(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(bottom = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            color = AuroraTheme.colors.accent,
            trackColor = Color.White.copy(alpha = 0.18f),
            strokeWidth = 3.dp,
        )
        Text(
            text = stringResource(MR.strings.reels_loading_more),
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.65f),
        )
    }
}

/**
 * Empty state for a search that returned no videos, with a hint and a reset action.
 */
@Composable
fun ReelsEmptySearchState(
    onResetSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.SearchOff,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(MR.strings.reels_empty_search_title),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(MR.strings.reels_empty_search_hint),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onResetSearch) {
            Text(
                text = stringResource(MR.strings.action_reset),
                color = AuroraTheme.colors.accent,
            )
        }
    }
}
