package com.remodex.mobile.ui.beta

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.remodex.mobile.beta.BetaFeedbackCategory

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BetaFeedbackSheet(
    visible: Boolean,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (BetaFeedbackCategory, String) -> Unit,
) {
    if (!visible) return

    var selectedCategory by rememberSaveable { mutableStateOf(BetaFeedbackCategory.Bug) }
    var message by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Send beta feedback",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Share what happened on this screen. Do not include secrets, prompts, or private project paths.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BetaFeedbackCategory.entries.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category.displayLabel) },
                    )
                }
            }
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 132.dp),
                minLines = 5,
                maxLines = 8,
                label = { Text("Feedback") },
            )
            Button(
                onClick = { onSubmit(selectedCategory, message.trim()) },
                enabled = !submitting && message.trim().isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (submitting) "Sending..." else "Send feedback")
            }
        }
    }
}

val BetaFeedbackCategory.displayLabel: String
    get() =
        when (this) {
            BetaFeedbackCategory.Bug -> "Bug"
            BetaFeedbackCategory.Crash -> "Crash"
            BetaFeedbackCategory.UxIssue -> "UX issue"
            BetaFeedbackCategory.ConfusingFlow -> "Confusing flow"
            BetaFeedbackCategory.Performance -> "Performance"
            BetaFeedbackCategory.FeatureRequest -> "Feature request"
            BetaFeedbackCategory.Other -> "Other"
        }
