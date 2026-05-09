package com.remodex.mobile.ui.agent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.composables.icons.lucide.R as LucideR
import com.remodex.mobile.R
import com.remodex.mobile.core.config.FeatureFlags
import com.remodex.mobile.core.transport.ConnectionState
import com.remodex.mobile.data.CodexRepository
import com.remodex.mobile.ui.home.RootReconnectRecoveryAction
import com.remodex.mobile.ui.home.RootReconnectUiState
import com.remodex.mobile.ui.navigation.AppRoutes
import com.remodex.mobile.ui.sidebar.SidebarScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max

private data class TrophySpotlightPx(
    val cx: Float,
    val cy: Float,
    val radiusPx: Float,
)

/**
 * Drawer sheet body: brand, search + threads (iOS-style list), footer links, Mac connection strip.
 * SwiftUI reference: [SidebarView](CodexMobile/CodexMobile/Views/SidebarView.swift).
 */
@Composable
fun SidebarDrawerContent(
    repository: CodexRepository,
    navController: NavHostController,
    drawerScope: CoroutineScope,
    drawerState: DrawerState,
    onOpenPairingScanner: () -> Unit,
    onReconnectSavedPairing: () -> Unit,
    onWakeSavedComputer: () -> Unit,
    closeDrawer: suspend () -> Unit,
    sessionReady: Boolean,
    connectionState: ConnectionState,
    reconnectUiState: RootReconnectUiState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var showTesterHqCoachmark by remember { mutableStateOf(false) }
    var coachmarkRootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var trophyLayoutCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var trophySpotlightPx by remember { mutableStateOf<TrophySpotlightPx?>(null) }

    LaunchedEffect(coachmarkRootCoords, trophyLayoutCoords, showTesterHqCoachmark) {
        if (!showTesterHqCoachmark) {
            trophySpotlightPx = null
            return@LaunchedEffect
        }
        val root = coachmarkRootCoords
        val trophy = trophyLayoutCoords
        if (root == null || trophy == null || !root.isAttached || !trophy.isAttached) {
            trophySpotlightPx = null
            return@LaunchedEffect
        }
        val topLeft = root.localPositionOf(trophy)
        val sz = trophy.size
        val cx = topLeft.x + sz.width / 2f
        val cy = topLeft.y + sz.height / 2f
        val r = max(sz.width, sz.height) / 2f * 1.38f
        trophySpotlightPx = TrophySpotlightPx(cx, cy, r)
    }

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen &&
            FeatureFlags.betaEngagementEnabled &&
            !SidebarTesterHqCoachmarkSession.shownThisProcess
        ) {
            SidebarTesterHqCoachmarkSession.shownThisProcess = true
            showTesterHqCoachmark = true
        }
        if (!drawerState.isOpen) {
            showTesterHqCoachmark = false
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .onGloballyPositioned { coachmarkRootCoords = it },
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(
                    painter = painterResource(LucideR.drawable.lucide_ic_terminal),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        scope.launch { runCatching { repository.refreshThreads() } }
                    },
                ) {
                    Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_refresh_cw),
                        contentDescription = stringResource(R.string.cd_refresh_thread_list),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 0.dp))
            SidebarScreen(
                repository = repository,
                onOpenArchivedChats = {
                    drawerScope.launch {
                        closeDrawer()
                        navController.navigate(AppRoutes.Archived)
                    }
                },
                onThreadSelected = closeDrawer,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
            )
            HorizontalDivider()
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
            ) {
                if (!sessionReady && connectionState !is ConnectionState.Connected) {
                    TextButton(
                        onClick = {
                            if (reconnectUiState.attempt == null) {
                                drawerScope.launch {
                                    closeDrawer()
                                    if (reconnectUiState.recoveryAction == RootReconnectRecoveryAction.ScanNewQr) {
                                        onOpenPairingScanner()
                                    } else if (reconnectUiState.wakeDisplayAvailable) {
                                        onWakeSavedComputer()
                                    } else {
                                        onReconnectSavedPairing()
                                    }
                                }
                            }
                        },
                        enabled = reconnectUiState.attempt == null,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text =
                                when {
                                    reconnectUiState.attempt != null ->
                                        if (reconnectUiState.isWakingDisplay) {
                                            stringResource(R.string.nav_reconnect_waking)
                                        } else {
                                            stringResource(R.string.nav_reconnect_connecting)
                                        }
                                    reconnectUiState.lastErrorMessage != null &&
                                        reconnectUiState.recoveryAction == RootReconnectRecoveryAction.ScanNewQr ->
                                        stringResource(R.string.nav_reconnect_scan_new_qr)
                                    reconnectUiState.wakeDisplayAvailable -> stringResource(R.string.nav_reconnect_wake)
                                    reconnectUiState.lastErrorMessage != null ->
                                        stringResource(R.string.nav_reconnect_retry)
                                    else -> stringResource(R.string.nav_reconnect)
                                },
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 10.dp),
            ) {
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    IconButton(
                        onClick = {
                            drawerScope.launch {
                                closeDrawer()
                                navController.navigate(AppRoutes.Settings)
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(LucideR.drawable.lucide_ic_settings),
                            contentDescription = stringResource(R.string.nav_settings),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    if (FeatureFlags.betaEngagementEnabled) {
                        IconButton(
                            onClick = {
                                drawerScope.launch {
                                    closeDrawer()
                                    navController.navigate(AppRoutes.TesterHq)
                                }
                            },
                            modifier = Modifier.onGloballyPositioned { trophyLayoutCoords = it },
                        ) {
                            Icon(
                                painter = painterResource(LucideR.drawable.lucide_ic_trophy),
                                contentDescription = stringResource(R.string.nav_tester_hq),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            drawerScope.launch {
                                closeDrawer()
                                onOpenPairingScanner()
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(LucideR.drawable.lucide_ic_scan_qr_code),
                            contentDescription = stringResource(R.string.nav_pairing_scan),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Text(
                    text = drawerFooterStatus(connectionState, sessionReady),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(start = 104.dp),
                )
            }
        }

        if (showTesterHqCoachmark && FeatureFlags.betaEngagementEnabled) {
            SidebarTesterHqCoachmarkOverlay(
                trophySpotlight = trophySpotlightPx,
                onDismiss = { showTesterHqCoachmark = false },
            )
        }
    }
}

private object SidebarTesterHqCoachmarkSession {
    /** Cleared when the process dies; each cold start gets one coachmark opportunity. */
    var shownThisProcess: Boolean = false
}

@Composable
private fun SidebarTesterHqCoachmarkOverlay(
    trophySpotlight: TrophySpotlightPx?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.48f)
    val shape = RoundedCornerShape(16.dp)
    val ringWidth = 2.dp
    val ringGap = 6.dp

    Box(modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss),
        ) {
            val spotlight = trophySpotlight
            if (spotlight != null) {
                val path =
                    Path().apply {
                        addRect(Rect(0f, 0f, size.width, size.height))
                        val hole =
                            Rect(
                                spotlight.cx - spotlight.radiusPx,
                                spotlight.cy - spotlight.radiusPx,
                                spotlight.cx + spotlight.radiusPx,
                                spotlight.cy + spotlight.radiusPx,
                            )
                        addOval(hole)
                        fillType = PathFillType.EvenOdd
                    }
                drawPath(path, scrimColor)
                drawCircle(
                    color = Color.White.copy(alpha = 0.45f),
                    radius = spotlight.radiusPx + ringGap.toPx(),
                    center = Offset(spotlight.cx, spotlight.cy),
                    style = Stroke(width = ringWidth.toPx()),
                )
            } else {
                drawRect(scrimColor)
            }
        }
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 72.dp)
                    .padding(horizontal = 4.dp)
                    .widthIn(max = 288.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.sidebar_tester_hq_coachmark_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.sidebar_tester_hq_coachmark_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.sidebar_tester_hq_coachmark_got_it))
                }
            }
        }
    }
}

@Composable
private fun drawerFooterStatus(
    conn: ConnectionState,
    sessionReady: Boolean,
): String {
    return when (conn) {
        ConnectionState.Offline -> stringResource(R.string.sidebar_bridge_offline)
        ConnectionState.Connecting -> stringResource(R.string.sidebar_bridge_connecting)
        ConnectionState.Connected ->
            if (sessionReady) {
                stringResource(R.string.sidebar_footer_connected_mac)
            } else {
                stringResource(R.string.sidebar_bridge_connecting)
            }
        is ConnectionState.Error ->
            stringResource(R.string.sidebar_bridge_error, conn.message)
        }
}
