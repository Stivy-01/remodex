package com.remodex.mobile.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.remodex.mobile.AppContainer
import com.remodex.mobile.R
import com.remodex.mobile.core.model.CodexPairingQRPayload
import com.remodex.mobile.data.CodexRepository
import com.remodex.mobile.data.QrPairingValidationResult
import com.remodex.mobile.data.validatePairingQrCode
import com.remodex.mobile.pairing.LoopbackRelayException
import com.remodex.mobile.pairing.applyQrPayloadAndConnect
import com.remodex.mobile.ui.dev.PairingQrScanner
import com.remodex.mobile.ui.home.BridgeUpdateSheet
import com.remodex.mobile.ui.theme.remodexScreenTopAppBarColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    repository: CodexRepository,
    onBack: () -> Unit,
    onPairingComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var relayHostOverride by remember { mutableStateOf("") }
    var tokenOverride by remember { mutableStateOf("") }
    var connecting by remember { mutableStateOf(false) }
    var bridgeUpdateTitle by remember { mutableStateOf("") }
    var bridgeUpdateMessage by remember { mutableStateOf("") }
    var bridgeUpdateCommand by remember { mutableStateOf<String?>(null) }
    var bridgeUpdateVisible by remember { mutableStateOf(false) }
    var scanEnabled by remember { mutableStateOf(true) }
    var pendingPayload by remember { mutableStateOf<CodexPairingQRPayload?>(null) }

    fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                errorMessage = context.getString(R.string.qr_scanner_camera_denied)
            }
        }

    fun runConnect() {
        val payload = pendingPayload ?: return
        scope.launch {
            connecting = true
            errorMessage = null
            try {
                applyQrPayloadAndConnect(
                    repository = repository,
                    sessionPersistence = AppContainer.sessionPersistence,
                    secureStore = AppContainer.secureStore,
                    payload = payload,
                    relayHostOverride = relayHostOverride,
                    tokenOverride = tokenOverride,
                )
                onPairingComplete()
            } catch (e: LoopbackRelayException) {
                errorMessage = e.message
            } catch (e: Exception) {
                errorMessage = e.message ?: e::class.simpleName
            } finally {
                connecting = false
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.qr_scanner_title)) },
                colors = remodexScreenTopAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
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
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.qr_scanner_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!hasCameraPermission()) {
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.qr_scanner_grant_camera))
                }
            } else if (scanEnabled && pendingPayload == null) {
                PairingQrScanner(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                    onDecodedPayload = { raw ->
                        errorMessage = null
                        when (val result = validatePairingQrCode(raw)) {
                            is QrPairingValidationResult.Success -> {
                                pendingPayload = result.payload
                                statusMessage = context.getString(R.string.qr_scanner_valid)
                                scanEnabled = false
                            }
                            is QrPairingValidationResult.ScanError -> {
                                errorMessage = result.message
                            }
                            is QrPairingValidationResult.BridgeUpdateRequired -> {
                                bridgeUpdateTitle = result.title
                                bridgeUpdateMessage = result.message
                                bridgeUpdateCommand = result.command
                                bridgeUpdateVisible = true
                            }
                        }
                    },
                )
            }
            OutlinedTextField(
                value = relayHostOverride,
                onValueChange = { relayHostOverride = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.qr_relay_host_override_label)) },
                placeholder = { Text(stringResource(R.string.qr_relay_host_override_placeholder)) },
                singleLine = true,
                enabled = !connecting,
            )
            OutlinedTextField(
                value = tokenOverride,
                onValueChange = { tokenOverride = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.qr_token_override_label)) },
                placeholder = { Text(stringResource(R.string.qr_token_override_placeholder)) },
                singleLine = true,
                enabled = !connecting,
            )
            if (pendingPayload != null) {
                Button(
                    onClick = { runConnect() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !connecting,
                ) {
                    Text(stringResource(R.string.qr_connect_button))
                }
                TextButton(
                    onClick = {
                        pendingPayload = null
                        scanEnabled = true
                        statusMessage = null
                        errorMessage = null
                    },
                    enabled = !connecting,
                ) {
                    Text(stringResource(R.string.qr_scan_again))
                }
            }
            statusMessage?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (connecting) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }

    BridgeUpdateSheet(
        visible = bridgeUpdateVisible,
        title = bridgeUpdateTitle,
        message = bridgeUpdateMessage,
        installCommand = bridgeUpdateCommand,
        onDismiss = {
            bridgeUpdateVisible = false
            bridgeUpdateCommand = null
        },
        onRetry = {
            bridgeUpdateVisible = false
            bridgeUpdateCommand = null
        },
        onScanNewQr = {
            bridgeUpdateVisible = false
            bridgeUpdateCommand = null
        },
    )
}
