package com.remodex.mobile.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.remodex.mobile.R
import com.remodex.mobile.core.model.PendingStructuredInputQuestion
import com.remodex.mobile.core.model.PendingStructuredInputRequest
import kotlinx.coroutines.launch

@Composable
internal fun StructuredInputDialog(
    request: PendingStructuredInputRequest,
    onSubmit: suspend (Map<String, List<String>>) -> Unit,
    onSkip: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isSubmitting by remember(request.id) { mutableStateOf(false) }
    var hasSubmittedResponse by remember(request.id) { mutableStateOf(false) }
    var currentQuestionIndex by remember(request.id) { mutableIntStateOf(0) }
    val typedAnswersByQuestionId = remember(request.id) { mutableStateMapOf<String, String>() }
    val selectedOptionByQuestionId = remember(request.id) { mutableStateMapOf<String, String>() }

    val questions = request.questions
    val lastQuestionIndex = questions.lastIndex
    val activeQuestion =
        if (questions.isEmpty()) {
            null
        } else {
            questions[currentQuestionIndex.coerceIn(0, lastQuestionIndex)]
        }
    val activeQuestionAnswered =
        activeQuestion?.let {
            hasStructuredInputAnswer(
                question = it,
                typedAnswersByQuestionId = typedAnswersByQuestionId,
                selectedOptionByQuestionId = selectedOptionByQuestionId,
            )
        } ?: true
    val payload = buildStructuredInputAnswersPayload(
        questions = questions,
        typedAnswersByQuestionId = typedAnswersByQuestionId,
        selectedOptionByQuestionId = selectedOptionByQuestionId,
    )
    val canSubmit = !isSubmitting && !hasSubmittedResponse && payload != null
    val canAdvance = !isSubmitting && !hasSubmittedResponse && activeQuestionAnswered
    val showStepper = questions.size > 1

    fun submitAnswers() {
        val answers = payload ?: return
        if (!canSubmit) return
        isSubmitting = true
        scope.launch {
            runCatching { onSubmit(answers) }
                .onSuccess { hasSubmittedResponse = true }
                .onFailure { isSubmitting = false }
        }
    }

    fun skipRequest() {
        if (isSubmitting || hasSubmittedResponse) return
        isSubmitting = true
        scope.launch {
            runCatching { onSkip() }
                .onSuccess { hasSubmittedResponse = true }
                .onFailure { isSubmitting = false }
        }
    }

    Dialog(
        onDismissRequest = { skipRequest() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResourceOrDefault(
                            request.questions.firstOrNull()?.header,
                            R.string.structured_input_default_title,
                        ),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResourceOrDefault(
                            request.questions.firstOrNull()?.question,
                            R.string.structured_input_default_body,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (showStepper && activeQuestion != null) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(
                                R.string.structured_input_question_n_of_m,
                                currentQuestionIndex + 1,
                                questions.size,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider()

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (activeQuestion == null) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.structured_input_default_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        StructuredInputQuestionPage(
                            question = activeQuestion,
                            typedValue = typedAnswersByQuestionId[activeQuestion.id].orEmpty(),
                            selectedValue = selectedOptionByQuestionId[activeQuestion.id],
                            onTypedValueChange = { value ->
                                typedAnswersByQuestionId[activeQuestion.id] = value
                            },
                            onSelectOption = { optionLabel ->
                                selectedOptionByQuestionId[activeQuestion.id] = optionLabel
                            },
                            isBusy = isSubmitting || hasSubmittedResponse,
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                        TextButton(
                            enabled = !isSubmitting && !hasSubmittedResponse,
                            onClick = { skipRequest() },
                        ) {
                            Text(
                                text =
                                    if (isSubmitting && !hasSubmittedResponse) {
                                        androidx.compose.ui.res.stringResource(R.string.structured_input_skipping)
                                    } else {
                                        androidx.compose.ui.res.stringResource(R.string.structured_input_skip)
                                    },
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    if (showStepper && currentQuestionIndex > 0) {
                        TextButton(
                            enabled = !isSubmitting && !hasSubmittedResponse,
                            onClick = { currentQuestionIndex -= 1 },
                        ) {
                            Text(text = androidx.compose.ui.res.stringResource(R.string.structured_input_previous))
                        }
                    }
                    if (showStepper && activeQuestion != null && currentQuestionIndex < lastQuestionIndex) {
                        TextButton(
                            enabled = canAdvance,
                            onClick = { currentQuestionIndex += 1 },
                        ) {
                            Text(text = androidx.compose.ui.res.stringResource(R.string.structured_input_next))
                        }
                    } else {
                        TextButton(
                            enabled = canSubmit,
                            onClick = { submitAnswers() },
                        ) {
                            if (isSubmitting && !hasSubmittedResponse) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = androidx.compose.ui.res.stringResource(R.string.structured_input_sending))
                            } else {
                                Text(text = androidx.compose.ui.res.stringResource(R.string.structured_input_send))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StructuredInputQuestionPage(
    question: PendingStructuredInputQuestion,
    typedValue: String,
    selectedValue: String?,
    onTypedValueChange: (String) -> Unit,
    onSelectOption: (String) -> Unit,
    isBusy: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = structuredInputQuestionTitle(question),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = structuredInputQuestionBody(question),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (question.options.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                question.options.forEach { option ->
                    val selected = selectedValue == option.label
                    FilterChip(
                        selected = selected,
                        enabled = !isBusy,
                        onClick = { onSelectOption(option.label) },
                        label = { Text(option.label) },
                    )
                    option.description
                        ?.takeIf { it.isNotBlank() }
                        ?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                }
            }
        }

        OutlinedTextField(
            value = typedValue,
            onValueChange = onTypedValueChange,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = androidx.compose.ui.res.stringResource(R.string.structured_input_field_label)) },
            singleLine = false,
            minLines = 2,
            maxLines = 4,
            visualTransformation =
                if (shouldMaskStructuredInput(question)) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
            keyboardOptions =
                KeyboardOptions(
                    capitalization =
                        if (shouldMaskStructuredInput(question)) {
                            KeyboardCapitalization.None
                        } else {
                            KeyboardCapitalization.Sentences
                        },
                    autoCorrectEnabled = !shouldMaskStructuredInput(question),
                ),
        )
    }
}

internal fun buildStructuredInputAnswersPayload(
    questions: List<PendingStructuredInputQuestion>,
    typedAnswersByQuestionId: Map<String, String>,
    selectedOptionByQuestionId: Map<String, String>,
): Map<String, List<String>>? {
    if (questions.isEmpty()) {
        return emptyMap()
    }

    val payload = linkedMapOf<String, List<String>>()
    questions.forEach { question ->
        val answer =
            typedAnswersByQuestionId[question.id]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { listOf(it) }
                ?: selectedOptionByQuestionId[question.id]
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { listOf(it) }

        if (answer == null) {
            return null
        }
        payload[question.id] = answer
    }
    return payload
}

internal fun hasStructuredInputAnswer(
    question: PendingStructuredInputQuestion,
    typedAnswersByQuestionId: Map<String, String>,
    selectedOptionByQuestionId: Map<String, String>,
): Boolean =
    typedAnswersByQuestionId[question.id]
        ?.trim()
        ?.isNotEmpty() == true ||
        selectedOptionByQuestionId[question.id]
            ?.trim()
            ?.isNotEmpty() == true

internal fun shouldMaskStructuredInput(question: PendingStructuredInputQuestion): Boolean {
    val text = listOf(question.header, question.question, question.id).joinToString(" ").lowercase()
    return listOf(
        "password",
        "passcode",
        "api key",
        "token",
        "secret",
        "credential",
        "credentials",
    ).any { it in text }
}

@Composable
private fun structuredInputQuestionTitle(question: PendingStructuredInputQuestion): String =
    question.header.trim().ifBlank {
        androidx.compose.ui.res.stringResource(R.string.structured_input_default_title)
    }

@Composable
private fun structuredInputQuestionBody(question: PendingStructuredInputQuestion): String =
    question.question.trim().ifBlank {
        question.id
    }

@Composable
private fun stringResourceOrDefault(value: String?, fallbackResId: Int): String =
    value?.trim()?.takeIf { it.isNotEmpty() }
        ?: androidx.compose.ui.res.stringResource(fallbackResId)
