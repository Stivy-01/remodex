package com.remodex.mobile.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.remodex.mobile.ui.theme.RemodexGitDiffAdditionBgDark
import com.remodex.mobile.ui.theme.RemodexGitDiffAdditionBgLight
import com.remodex.mobile.ui.theme.RemodexGitDiffDeletionBgDark
import com.remodex.mobile.ui.theme.RemodexGitDiffDeletionBgLight
import com.remodex.mobile.ui.theme.RemodexGitDiffMetaBgDark
import com.remodex.mobile.ui.theme.RemodexGitDiffMetaBgLight
import com.remodex.mobile.ui.theme.isAgentLightChrome

internal enum class GitPatchLineKind {
    Meta,
    Addition,
    Deletion,
    Context,
}

private val GitPatchHunkHeaderRegex = Regex("""@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@""")

/** Classify unified-diff / git patch lines for row highlighting. */
internal fun gitPatchLineKind(line: String): GitPatchLineKind {
    if (line.startsWith("diff --git") ||
        line.startsWith("index ") ||
        line.startsWith("---") ||
        line.startsWith("+++") ||
        line.startsWith("@@") ||
        line.startsWith("new file mode") ||
        line.startsWith("deleted file mode") ||
        line.startsWith("similarity index ") ||
        line.startsWith("rename from ") ||
        line.startsWith("rename to ") ||
        line.startsWith("Binary files ")
    ) {
        return GitPatchLineKind.Meta
    }
    if (line.startsWith("+")) return GitPatchLineKind.Addition
    if (line.startsWith("-")) return GitPatchLineKind.Deletion
    if (line.startsWith("\\")) return GitPatchLineKind.Meta
    return GitPatchLineKind.Context
}

@Composable
internal fun GitPatchHighlightedBlock(
    patch: String,
    modifier: Modifier = Modifier,
    verticalScrollEnabled: Boolean = true,
) {
    val lines =
        remember(patch) {
            patch.replace("\r\n", "\n").split('\n')
        }
    /** Use dark washes only for app graphite theme; system night mode alone must not switch palette. */
    val useDarkDiffSurfaces = !isAgentLightChrome()
    val addBg =
        if (useDarkDiffSurfaces) RemodexGitDiffAdditionBgDark else RemodexGitDiffAdditionBgLight
    val delBg =
        if (useDarkDiffSurfaces) RemodexGitDiffDeletionBgDark else RemodexGitDiffDeletionBgLight
    val metaBg =
        if (useDarkDiffSurfaces) RemodexGitDiffMetaBgDark else RemodexGitDiffMetaBgLight
    val fg = MaterialTheme.colorScheme.onSurface
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val linePadH = 6.dp
    val linePadV = 3.dp
    val lineNumberColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    val gutterBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (verticalScrollEnabled) {
                        Modifier.verticalScroll(vScroll)
                    } else {
                        Modifier
                    },
                ),
    ) {
        Row(
            modifier = Modifier.horizontalScroll(hScroll),
        ) {
            Column {
                var oldLineNumber: Int? = null
                var newLineNumber: Int? = null
                lines.forEach { line ->
                    val kind = gitPatchLineKind(line)
                    if (line.startsWith("@@")) {
                        GitPatchHunkHeaderRegex.find(line)?.let { match ->
                            oldLineNumber = match.groupValues.getOrNull(1)?.toIntOrNull()
                            newLineNumber = match.groupValues.getOrNull(2)?.toIntOrNull()
                        }
                    }
                    val displayLineNumber =
                        when (kind) {
                            GitPatchLineKind.Addition -> newLineNumber?.also { newLineNumber = it + 1 }
                            GitPatchLineKind.Deletion -> oldLineNumber?.also { oldLineNumber = it + 1 }
                            GitPatchLineKind.Context -> newLineNumber?.also {
                                newLineNumber = it + 1
                                oldLineNumber = oldLineNumber?.plus(1)
                            }
                            GitPatchLineKind.Meta -> null
                        }
                    val bg: Color? =
                        when (kind) {
                            GitPatchLineKind.Addition -> addBg
                            GitPatchLineKind.Deletion -> delBg
                            GitPatchLineKind.Meta -> metaBg
                            GitPatchLineKind.Context -> null
                        }
                    val rowMod =
                        Modifier
                            .then(
                                if (bg != null) {
                                    Modifier.background(bg)
                                } else {
                                    Modifier
                                },
                            )
                    Row(modifier = rowMod) {
                        Text(
                            text = displayLineNumber?.toString().orEmpty(),
                            modifier =
                                Modifier
                                    .width(44.dp)
                                    .background(gutterBg)
                                    .padding(horizontal = linePadH, vertical = linePadV),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = lineNumberColor,
                            textAlign = TextAlign.End,
                            softWrap = false,
                        )
                        Text(
                            text = line.ifEmpty { " " },
                            modifier = Modifier.padding(horizontal = linePadH, vertical = linePadV),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = fg,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}
