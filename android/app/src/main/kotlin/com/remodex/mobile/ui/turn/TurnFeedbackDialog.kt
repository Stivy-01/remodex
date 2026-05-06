package com.remodex.mobile.ui.turn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.remodex.mobile.R

internal data class TurnFeedbackSubmission(
    val category: TurnFeedbackCategory,
    val description: String,
    val includeSessionLogs: Boolean,
)

internal enum class TurnFeedbackCategory {
    Bug,
    BadResult,
    GoodResult,
    SafetyCheck,
    Other,
}

@Composable
internal fun TurnFeedbackDialog(
    onDismiss: () -> Unit,
    onSubmit: (TurnFeedbackSubmission) -> Unit,
) {
    var selectedCategory by rememberSaveable { mutableStateOf(TurnFeedbackCategory.Bug) }
    var description by rememberSaveable { mutableStateOf("") }
    var includeLogs by rememberSaveable { mutableStateOf(true) }
    val categoryLabels =
        remember {
            listOf(
                TurnFeedbackCategory.Bug to R.string.turn_feedback_category_bug,
                TurnFeedbackCategory.BadResult to R.string.turn_feedback_category_bad_result,
                TurnFeedbackCategory.GoodResult to R.string.turn_feedback_category_good_result,
                TurnFeedbackCategory.SafetyCheck to R.string.turn_feedback_category_safety_check,
                TurnFeedbackCategory.Other to R.string.turn_feedback_category_other,
            )
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Feedback,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(stringResource(R.string.turn_feedback_title)) },
        text = {
            Column(
                modifier = Modifier.widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                FeedbackCategoryRows(
                    categories = categoryLabels,
                    selectedCategory = selectedCategory,
                    onSelect = { selectedCategory = it },
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 132.dp),
                    minLines = 5,
                    maxLines = 8,
                    label = { Text(stringResource(R.string.turn_feedback_description_label)) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = includeLogs,
                        onCheckedChange = { includeLogs = it },
                    )
                    Text(
                        text = stringResource(R.string.turn_feedback_include_logs),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = description.trim().isNotEmpty(),
                onClick = {
                    onSubmit(
                        TurnFeedbackSubmission(
                            category = selectedCategory,
                            description = description.trim(),
                            includeSessionLogs = includeLogs,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.turn_feedback_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.turn_feedback_cancel))
            }
        },
    )
}

@Composable
private fun FeedbackCategoryRows(
    categories: List<Pair<TurnFeedbackCategory, Int>>,
    selectedCategory: TurnFeedbackCategory,
    onSelect: (TurnFeedbackCategory) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.chunked(2).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEach { (category, labelRes) ->
                    val selected = category == selectedCategory
                    FilterChip(
                        selected = selected,
                        onClick = { onSelect(category) },
                        label = { Text(stringResource(labelRes)) },
                        leadingIcon =
                            if (selected) {
                                {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                    )
                                }
                            } else {
                                null
                            },
                    )
                }
            }
        }
    }
}
