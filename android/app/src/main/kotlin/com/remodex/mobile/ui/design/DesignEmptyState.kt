package com.remodex.mobile.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.remodex.mobile.R

private val quickTemplates = listOf(
    "Onboarding" to "Create onboarding screen for a ride tracker",
    "Paywall" to "Create a paywall screen for premium subscription",
    "Settings" to "Create a settings screen with profile and preferences",
    "Chat screen" to "Create a chat screen with message bubbles",
    "Dashboard" to "Create a dashboard with stats cards",
    "Mobile landing" to "Create a landing page for a mobile app",
)

@Composable
fun DesignEmptyState(
    promptText: String,
    onPromptTextChanged: (String) -> Unit,
    onSubmitPrompt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Create a UI design",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Describe a screen and let the agent generate a visual draft.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = promptText,
            onValueChange = onPromptTextChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Describe the screen...") },
            minLines = 2,
            maxLines = 4,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onSubmitPrompt,
            modifier = Modifier.fillMaxWidth(),
            enabled = promptText.isNotBlank(),
        ) {
            Text("Generate")
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Quick templates",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            quickTemplates.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { (label, prompt) ->
                        SuggestionChip(
                            onClick = {
                                onPromptTextChanged(prompt)
                            },
                            label = { Text(label, maxLines = 1) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}
