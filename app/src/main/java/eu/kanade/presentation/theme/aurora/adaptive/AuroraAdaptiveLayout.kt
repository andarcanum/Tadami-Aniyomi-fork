package eu.kanade.presentation.theme.aurora.adaptive

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

fun Modifier.auroraCenteredMaxWidth(maxWidthDp: Int?): Modifier {
    return if (maxWidthDp == null) {
        this
    } else {
        fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = maxWidthDp.dp)
    }
}
