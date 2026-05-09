package com.remodex.mobile.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.net.Uri
import android.provider.Settings
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import com.remodex.mobile.R
import com.remodex.mobile.AppContainer
import com.remodex.mobile.core.config.FeatureFlags
import com.remodex.mobile.core.model.AppFontStyle
import com.remodex.mobile.core.model.AppLanguagePreference
import com.remodex.mobile.core.model.AppThemePreference
import com.remodex.mobile.core.model.ContextWindowUsage
import com.remodex.mobile.core.model.CodexRateLimitBucket
import com.remodex.mobile.core.notification.LocalNotificationSettings
import com.remodex.mobile.core.transport.ConnectionState
import com.remodex.mobile.data.AppFontPreferences
import com.remodex.mobile.data.CodexRepository
import com.remodex.mobile.data.LanguagePreferences
import com.remodex.mobile.data.ThemePreferences
import com.remodex.mobile.ui.shared.UsageStatusSummary
import com.remodex.mobile.ui.theme.remodexScreenTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: CodexRepository,
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToWhatsNew: () -> Unit,
    onNavigateToTesterHq: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onNavigateBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val conn by repository.connectionState.collectAsStateWithLifecycle()
    var fontStyle by remember { mutableStateOf(AppFontPreferences.readFontStyle(context)) }
    var languagePreference by remember { mutableStateOf(LanguagePreferences.read(context)) }
    var themePreference by remember { mutableStateOf(ThemePreferences.read(context)) }
    var localRelayHostOverride by remember {
        mutableStateOf(AppContainer.sessionPersistence.loadLocalRelayHostOverride().orEmpty())
    }
    val versionName = remember { readAppVersionName(context) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                colors = remodexScreenTopAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_section_appearance),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.settings_font_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppFontStyle.entries.forEach { option ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == fontStyle,
                                onClick = {
                                    fontStyle = option
                                    AppFontPreferences.writeFontStyle(context, option)
                                },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = option == fontStyle,
                        onClick = null,
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(text = option.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = option.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.settings_language_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.settings_language_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppLanguagePreference.entries.forEach { option ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == languagePreference,
                                onClick = {
                                    languagePreference = option
                                    LanguagePreferences.write(context, option)
                                },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = option == languagePreference,
                        onClick = null,
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = stringResource(settingsLanguageTitleRes(option)),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(settingsLanguageSubtitleRes(option)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.settings_theme_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.settings_theme_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppThemePreference.entries.forEach { option ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == themePreference,
                                onClick = {
                                    themePreference = option
                                    ThemePreferences.write(context, option)
                                },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = option == themePreference,
                        onClick = null,
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = stringResource(settingsThemeTitleRes(option)),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(settingsThemeSubtitleRes(option)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.settings_section_connection),
                style = MaterialTheme.typography.titleMedium,
            )
            SettingsConnectionStatus(conn = conn)
            OutlinedTextField(
                value = localRelayHostOverride,
                onValueChange = {
                    localRelayHostOverride = it
                    AppContainer.sessionPersistence.saveLocalRelayHostOverride(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_local_relay_host_override_label)) },
                placeholder = { Text(stringResource(R.string.settings_local_relay_host_override_placeholder)) },
                singleLine = true,
            )
            SettingsNotificationSection(context = context)

            SettingsUsageRateLimitsSection(repository = repository)

            Text(
                text = stringResource(R.string.settings_section_about),
                style = MaterialTheme.typography.titleMedium,
            )
            SettingsNavigationRow(
                title = stringResource(R.string.nav_about_remodex),
                subtitle = stringResource(R.string.settings_about_remodex_hint),
                onClick = {
                    scope.launch {
                        AppContainer.betaEngagementRepository.recordMissionEvent(
                            eventType = "about_screen_opened",
                            screen = "settings",
                        )
                    }
                    onNavigateToAbout()
                },
            )
            SettingsNavigationRow(
                title = stringResource(R.string.nav_whats_new),
                subtitle = stringResource(R.string.settings_whats_new_hint),
                onClick = {
                    scope.launch {
                        AppContainer.betaEngagementRepository.recordMissionEvent(
                            eventType = "settings_whats_new_opened",
                            screen = "settings",
                        )
                    }
                    onNavigateToWhatsNew()
                },
            )
            if (FeatureFlags.betaEngagementEnabled) {
                SettingsNavigationRow(
                    title = stringResource(R.string.nav_tester_hq),
                    subtitle = stringResource(R.string.settings_tester_hq_hint),
                    onClick = {
                        scope.launch {
                            AppContainer.betaEngagementRepository.recordMissionEvent(
                                eventType = "settings_tester_hq_entry_opened",
                                screen = "settings",
                            )
                        }
                        onNavigateToTesterHq()
                    },
                )
            }
            Text(
                text = stringResource(R.string.settings_about_version, versionName),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.settings_more_coming),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsNotificationSection(context: Context) {
    var refreshNonce by remember { mutableStateOf(0) }
    var requestedInSession by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            requestedInSession = true
            refreshNonce++
        }
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    refreshNonce++
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionStatus =
        remember(refreshNonce, context) {
            LocalNotificationSettings.permissionStatus(context)
        }
    val notificationsEnabled =
        permissionStatus == LocalNotificationSettings.PermissionStatus.Granted ||
            permissionStatus == LocalNotificationSettings.PermissionStatus.NotRequired

    Text(
        text = stringResource(R.string.settings_section_notifications),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = stringResource(R.string.settings_notifications_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_notifications_run_completion_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(
                    if (notificationsEnabled) {
                        R.string.settings_notifications_status_enabled
                    } else {
                        R.string.settings_notifications_status_disabled
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (notificationsEnabled) {
        Text(
            text = stringResource(R.string.settings_notifications_enabled_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(
            text = stringResource(R.string.settings_notifications_disabled_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (
            Build.VERSION.SDK_INT >= 33 &&
            permissionStatus == LocalNotificationSettings.PermissionStatus.RuntimePermissionRequired &&
            !requestedInSession
        ) {
            TextButton(
                onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            ) {
                Text(stringResource(R.string.settings_notifications_request_permission))
            }
        } else {
            TextButton(
                onClick = { openSystemNotificationSettings(context) },
            ) {
                Text(stringResource(R.string.settings_notifications_open_system_settings))
            }
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(
                    selected = false,
                    onClick = onClick,
                    role = Role.Button,
                )
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsUsageRateLimitsSection(
    repository: CodexRepository,
) {
    val sessionReady by repository.isSessionReady.collectAsStateWithLifecycle()
    val conn by repository.connectionState.collectAsStateWithLifecycle()
    val hasResolved by repository.hasResolvedRateLimitsSnapshot.collectAsStateWithLifecycle()
    val isLoading by repository.isLoadingRateLimits.collectAsStateWithLifecycle()
    val err by repository.rateLimitsErrorMessage.collectAsStateWithLifecycle()
    val buckets by repository.rateLimitBuckets.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(sessionReady, conn, hasResolved, err, isLoading) {
        if (sessionReady &&
            conn is ConnectionState.Connected &&
            !hasResolved &&
            err == null &&
            !isLoading
        ) {
            runCatching { repository.refreshRateLimits() }
        }
    }

    val displayRows = remember(buckets) { CodexRateLimitBucket.visibleDisplayRows(buckets) }
    val refreshCd = stringResource(R.string.cd_usage_rate_limits_refresh)
    val activeThreadId by repository.activeThreadId.collectAsStateWithLifecycle()
    val contextUsageMap by repository.contextWindowUsageByThread.collectAsStateWithLifecycle()
    val contextLoading by repository.contextWindowUsageLoadingThreads.collectAsStateWithLifecycle()
    val contextErrors by repository.contextWindowUsageErrorByThread.collectAsStateWithLifecycle()

    Text(
        text = stringResource(R.string.settings_section_usage),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = stringResource(R.string.usage_rate_limits_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val usageSummary =
        activeThreadId
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { tid -> contextUsageMap[tid] }
    UsageStatusSummary(
        contextUsage = usageSummary,
        rateLimitRows = displayRows,
        loading = isLoading,
    )

    Text(
        text = stringResource(R.string.usage_context_window_title),
        style = MaterialTheme.typography.titleSmall,
    )
    Text(
        text = stringResource(R.string.usage_context_window_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    when {
        activeThreadId.isNullOrBlank() -> {
            Text(
                text = stringResource(R.string.usage_context_window_no_thread),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        !sessionReady || conn !is ConnectionState.Connected -> {
            Text(
                text = stringResource(R.string.usage_rate_limits_offline),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        else -> {
            val tid = activeThreadId.orEmpty().trim()
            val usage: ContextWindowUsage? = contextUsageMap[tid]
            val ctxLoading = contextLoading.contains(tid)
            val ctxErr = contextErrors[tid]
            val ctxRefreshCd = stringResource(R.string.cd_usage_context_window_refresh)
            val ctxLoadingCd = stringResource(R.string.cd_usage_context_window_loading)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { scope.launch { runCatching { repository.refreshContextWindowUsage(tid) } } },
                    enabled = !ctxLoading,
                    modifier = Modifier.semantics { contentDescription = ctxRefreshCd },
                ) {
                    Text(stringResource(R.string.usage_context_window_refresh))
                }
                if (ctxLoading) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier =
                            Modifier
                                .size(20.dp)
                                .align(Alignment.CenterVertically)
                                .semantics { contentDescription = ctxLoadingCd },
                        strokeWidth = 2.dp,
                    )
                }
            }
            ctxErr?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (!ctxLoading && ctxErr == null && usage == null) {
                Text(
                    text = stringResource(R.string.usage_context_window_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            usage?.let { u ->
                Text(
                    text =
                        stringResource(
                            R.string.usage_context_window_tokens,
                            u.tokensUsedFormatted,
                            u.tokenLimitFormatted,
                            u.percentUsed,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { u.fractionUsed.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    Text(
        text = stringResource(R.string.usage_account_limits_title),
        style = MaterialTheme.typography.titleSmall,
    )

    if (!sessionReady || conn !is ConnectionState.Connected) {
        Text(
            text = stringResource(R.string.usage_rate_limits_offline),
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { scope.launch { runCatching { repository.refreshRateLimits() } } },
                enabled = !isLoading,
                modifier = Modifier.semantics { contentDescription = refreshCd },
            ) {
                Text(stringResource(R.string.usage_rate_limits_refresh))
            }
            if (isLoading) {
                Spacer(modifier = Modifier.width(8.dp))
                val loadingCd = stringResource(R.string.cd_usage_rate_limits_loading)
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .size(20.dp)
                            .align(Alignment.CenterVertically)
                            .semantics { contentDescription = loadingCd },
                    strokeWidth = 2.dp,
                )
            }
        }
        err?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (!isLoading && err == null && hasResolved && displayRows.isEmpty()) {
            Text(
                text = stringResource(R.string.usage_rate_limits_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            displayRows.forEach { row ->
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { row.window.clampedUsedPercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.usage_rate_limits_percent, row.window.clampedUsedPercent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsConnectionStatus(conn: ConnectionState) {
    val text =
        when (val c = conn) {
            ConnectionState.Offline -> stringResource(R.string.sidebar_bridge_offline)
            ConnectionState.Connecting -> stringResource(R.string.sidebar_bridge_connecting)
            ConnectionState.Connected -> stringResource(R.string.sidebar_bridge_connected)
            is ConnectionState.Error -> stringResource(R.string.sidebar_bridge_error, c.message)
        }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
    )
}

private fun settingsThemeTitleRes(option: AppThemePreference): Int =
    when (option) {
        AppThemePreference.system -> R.string.settings_theme_system_title
        AppThemePreference.light -> R.string.settings_theme_light_title
        AppThemePreference.dark -> R.string.settings_theme_dark_title
    }

private fun settingsThemeSubtitleRes(option: AppThemePreference): Int =
    when (option) {
        AppThemePreference.system -> R.string.settings_theme_system_subtitle
        AppThemePreference.light -> R.string.settings_theme_light_subtitle
        AppThemePreference.dark -> R.string.settings_theme_dark_subtitle
    }

private fun settingsLanguageTitleRes(option: AppLanguagePreference): Int =
    when (option) {
        AppLanguagePreference.english -> R.string.settings_language_english_title
        AppLanguagePreference.system -> R.string.settings_language_system_title
    }

private fun settingsLanguageSubtitleRes(option: AppLanguagePreference): Int =
    when (option) {
        AppLanguagePreference.english -> R.string.settings_language_english_subtitle
        AppLanguagePreference.system -> R.string.settings_language_system_subtitle
    }

private fun readAppVersionName(context: Context): String =
    try {
        val pm = context.packageManager
        val pkg = context.packageName
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).versionName ?: "0.1.0"
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0).versionName ?: "0.1.0"
        }
    } catch (_: Exception) {
        "0.1.0"
    }

private fun openSystemNotificationSettings(context: Context) {
    val appPackage = Uri.fromParts("package", context.packageName, null)
    val intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    runCatching { context.startActivity(intent) }
        .recoverCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = appPackage
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
}
