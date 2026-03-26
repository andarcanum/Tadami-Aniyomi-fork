package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.util.Screen

object SecretHallOfFameScreen : Screen() {
    @Composable
    override fun Content() {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = secretHallPublicStubMessage(),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

internal fun secretHallPublicStubTitle(): String = "Secret Hall unavailable"

internal fun secretHallPublicStubMessage(): String {
    return "This build does not include the local Secret Hall implementation."
}
