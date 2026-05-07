package com.remodex.mobile.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.remodex.mobile.core.model.GitDiffTotals
import com.remodex.mobile.core.model.GitRepoSyncResult
import com.remodex.mobile.ui.agent.GitNodeConnectorIcon
import com.remodex.mobile.ui.theme.RemodexGitAddition

enum class GitActionSheetMode {
    commit,
    push,
    createPullRequest,
}

enum class GitActionNextStep {
    commit,
    commitAndPush,
    commitPushAndPullRequest,
    push,
    pushAndPullRequest,
    createPullRequest,
}

data class GitActionSheetSubmission(
    val commitMessage: String,
    val pullRequestTitle: String,
    val pullRequestBody: String,
    val baseBranch: String,
    val nextStep: GitActionNextStep,
    val pushRemoteName: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitActionBottomSheet(
    visible: Boolean,
    mode: GitActionSheetMode?,
    initialNextStep: GitActionNextStep?,
    status: GitRepoSyncResult?,
    defaultBaseBranch: String?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (GitActionSheetSubmission) -> Unit,
) {
    if (!visible || mode == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var commitMessage by remember(mode) { mutableStateOf("") }
    var pullRequestTitle by remember(mode) { mutableStateOf("") }
    var pullRequestBody by remember(mode) { mutableStateOf("") }
    var baseBranch by remember(mode, defaultBaseBranch) { mutableStateOf(defaultBaseBranch.orEmpty()) }
    var pushToForkRemote by remember(mode) { mutableStateOf(false) }
    var selectedNextStep by remember(mode, initialNextStep) {
        mutableStateOf(initialNextStepForMode(mode, initialNextStep))
    }

    LaunchedEffect(mode, initialNextStep) {
        selectedNextStep = initialNextStepForMode(mode, initialNextStep)
    }
    LaunchedEffect(selectedNextStep) {
        if (!selectedNextStep.usesPushRemote) {
            pushToForkRemote = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!isBusy) onDismiss()
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GitActionSheetHeader(mode = mode, onDismiss = onDismiss, dismissEnabled = !isBusy)
            GitActionBranchRow(status = status, defaultBaseBranch = defaultBaseBranch, mode = mode)
            GitActionChangesRow(status = status)

            when (mode) {
                GitActionSheetMode.commit -> {
                    OutlinedTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        enabled = !isBusy,
                        label = { Text("Commit message") },
                        placeholder = { Text("Leave blank to generate automatically") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                GitActionSheetMode.push -> {
                    Text(
                        text = "Pushes your local commits to the remote repository.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PullRequestFields(
                        title = pullRequestTitle,
                        onTitleChange = { pullRequestTitle = it },
                        body = pullRequestBody,
                        onBodyChange = { pullRequestBody = it },
                        baseBranch = baseBranch,
                        onBaseBranchChange = { baseBranch = it },
                        enabled = !isBusy,
                        showBase = selectedNextStep == GitActionNextStep.pushAndPullRequest,
                    )
                }
                GitActionSheetMode.createPullRequest -> {
                    PullRequestFields(
                        title = pullRequestTitle,
                        onTitleChange = { pullRequestTitle = it },
                        body = pullRequestBody,
                        onBodyChange = { pullRequestBody = it },
                        baseBranch = baseBranch,
                        onBaseBranchChange = { baseBranch = it },
                        enabled = !isBusy,
                        showBase = true,
                    )
                }
            }

            if (selectedNextStep.usesPushRemote) {
                ForkRemoteCheckbox(
                    checked = pushToForkRemote,
                    enabled = !isBusy,
                    onCheckedChange = { pushToForkRemote = it },
                )
            }

            Text(
                text = "Next step",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GitActionNextSteps(
                mode = mode,
                selected = selectedNextStep,
                enabled = !isBusy,
                onSelected = { selectedNextStep = it },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        onSubmit(
                            GitActionSheetSubmission(
                                commitMessage = commitMessage,
                                pullRequestTitle = pullRequestTitle,
                                pullRequestBody = pullRequestBody,
                                baseBranch = baseBranch,
                                nextStep = selectedNextStep,
                                pushRemoteName = if (pushToForkRemote) "fork" else null,
                            ),
                        )
                    },
                    enabled = !isBusy && canSubmit(mode, selectedNextStep, baseBranch),
                    modifier = Modifier.weight(1.4f),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Continue")
                    }
                }
            }
        }
    }
}

@Composable
private fun ForkRemoteCheckbox(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Push to fork remote",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Runs git push fork HEAD instead of pushing to the tracked remote.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GitActionSheetHeader(
    mode: GitActionSheetMode,
    onDismiss: () -> Unit,
    dismissEnabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                GitNodeConnectorIcon(
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Text(
            text =
                when (mode) {
                    GitActionSheetMode.commit -> "Commit changes"
                    GitActionSheetMode.push -> "Push changes"
                    GitActionSheetMode.createPullRequest -> "Create pull request"
                },
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss, enabled = dismissEnabled) {
            Text("Close")
        }
    }
}

@Composable
private fun GitActionBranchRow(
    status: GitRepoSyncResult?,
    defaultBaseBranch: String?,
    mode: GitActionSheetMode,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LabelValueRow(
            label = "Branch",
            value = status?.currentBranch?.takeIf { it.isNotBlank() } ?: "unknown",
        )
        if (mode == GitActionSheetMode.createPullRequest) {
            LabelValueRow(
                label = "Base",
                value = defaultBaseBranch?.takeIf { it.isNotBlank() } ?: "unknown",
            )
        }
    }
}

@Composable
private fun GitActionChangesRow(status: GitRepoSyncResult?) {
    val totals = status?.workingTreeDiffTotals
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Changes",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "${status?.files?.size ?: 0} files",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (totals != null) {
                GitDiffTotalsText(totals)
            }
        }
    }
}

@Composable
private fun GitDiffTotalsText(totals: GitDiffTotals) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "+${totals.additions}",
            style = MaterialTheme.typography.labelLarge,
            color = RemodexGitAddition,
        )
        Text(
            text = "-${totals.deletions}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun PullRequestFields(
    title: String,
    onTitleChange: (String) -> Unit,
    body: String,
    onBodyChange: (String) -> Unit,
    baseBranch: String,
    onBaseBranchChange: (String) -> Unit,
    enabled: Boolean,
    showBase: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showBase) {
            OutlinedTextField(
                value = baseBranch,
                onValueChange = onBaseBranchChange,
                enabled = enabled,
                label = { Text("Base branch") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            enabled = enabled,
            label = { Text("Title") },
            placeholder = { Text("Leave blank to generate") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = body,
            onValueChange = onBodyChange,
            enabled = enabled,
            label = { Text("Description") },
            placeholder = { Text("Leave blank to generate") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GitActionNextSteps(
    mode: GitActionSheetMode,
    selected: GitActionNextStep,
    enabled: Boolean,
    onSelected: (GitActionNextStep) -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        nextStepsFor(mode).forEachIndexed { index, step ->
            GitActionNextStepRow(
                title = nextStepTitle(step),
                detail = nextStepDetail(step),
                selected = selected == step,
                enabled = enabled,
                onClick = { onSelected(step) },
            )
            if (index != nextStepsFor(mode).lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun GitActionNextStepRow(
    title: String,
    detail: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LabelValueRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun defaultNextStep(mode: GitActionSheetMode): GitActionNextStep =
    when (mode) {
        GitActionSheetMode.commit -> GitActionNextStep.commit
        GitActionSheetMode.push -> GitActionNextStep.push
        GitActionSheetMode.createPullRequest -> GitActionNextStep.createPullRequest
    }

private fun initialNextStepForMode(
    mode: GitActionSheetMode,
    initialNextStep: GitActionNextStep?,
): GitActionNextStep =
    initialNextStep
        ?.takeIf { it in nextStepsFor(mode) }
        ?: defaultNextStep(mode)

private fun nextStepsFor(mode: GitActionSheetMode): List<GitActionNextStep> =
    when (mode) {
        GitActionSheetMode.commit ->
            listOf(
                GitActionNextStep.commit,
                GitActionNextStep.commitAndPush,
                GitActionNextStep.commitPushAndPullRequest,
            )
        GitActionSheetMode.push ->
            listOf(
                GitActionNextStep.push,
                GitActionNextStep.pushAndPullRequest,
            )
        GitActionSheetMode.createPullRequest ->
            listOf(GitActionNextStep.createPullRequest)
    }

private fun nextStepTitle(step: GitActionNextStep): String =
    when (step) {
        GitActionNextStep.commit -> "Commit"
        GitActionNextStep.commitAndPush -> "Commit and push"
        GitActionNextStep.commitPushAndPullRequest -> "Commit, push and create PR"
        GitActionNextStep.push -> "Push"
        GitActionNextStep.pushAndPullRequest -> "Push and create PR"
        GitActionNextStep.createPullRequest -> "Create PR"
    }

private fun nextStepDetail(step: GitActionNextStep): String? =
    when (step) {
        GitActionNextStep.commitPushAndPullRequest,
        GitActionNextStep.pushAndPullRequest,
        GitActionNextStep.createPullRequest,
        -> "Opens GitHub with generated title and body if fields are empty."
        else -> null
    }

private val GitActionNextStep.usesPushRemote: Boolean
    get() =
        when (this) {
            GitActionNextStep.commitAndPush,
            GitActionNextStep.push,
            -> true
            GitActionNextStep.commit,
            GitActionNextStep.commitPushAndPullRequest,
            GitActionNextStep.pushAndPullRequest,
            GitActionNextStep.createPullRequest,
            -> false
        }

private fun canSubmit(
    mode: GitActionSheetMode,
    step: GitActionNextStep,
    baseBranch: String,
): Boolean =
    when (mode) {
        GitActionSheetMode.commit ->
            step != GitActionNextStep.commitPushAndPullRequest || baseBranch.isNotBlank()
        GitActionSheetMode.push ->
            step != GitActionNextStep.pushAndPullRequest || baseBranch.isNotBlank()
        GitActionSheetMode.createPullRequest -> baseBranch.isNotBlank()
    }
