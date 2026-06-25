package eu.kanade.presentation.more.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

internal class DisclaimerStep : OnboardingStep {

    private var accepted by mutableStateOf(false)

    override val isComplete: Boolean
        get() = accepted

    @Composable
    override fun Content() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(MR.strings.onboarding_disclaimer_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(MR.strings.onboarding_disclaimer_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { accepted = !accepted },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                Checkbox(
                    checked = accepted,
                    onCheckedChange = { accepted = it },
                )
                Spacer(modifier = Modifier.height(0.dp))
                Text(
                    text = stringResource(MR.strings.onboarding_disclaimer_accept),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
