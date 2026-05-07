package com.remodex.mobile.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.launch

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

    fun resetScanner() {
        pendingPayload = null
        scanEnabled = true
        statusMessage = null
        errorMessage = null
    }

    fun runConnect(payload: CodexPairingQRPayload) {
        scope.launch {
            connecting = true
            errorMessage = null
            statusMessage = context.getString(R.string.qr_scanner_valid)
            try {
                applyQrPayloadAndConnect(
                    repository = repository,
                    sessionPersistence = AppContainer.sessionPersistence,
                    secureStore = AppContainer.secureStore,
                    payload = payload,
                    relayHostOverride = "",
                    tokenOverride = "",
                )
                onPairingComplete()
            } catch (e: LoopbackRelayException) {
                errorMessage = e.message
                pendingPayload = null
            } catch (e: Exception) {
                errorMessage = e.message ?: e::class.simpleName
                pendingPayload = null
            } finally {
                connecting = false
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFFF4F3F1),
        contentColor = Color(0xFF151515),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QrScannerHeader(onBack = onBack)

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.64f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF3B332C)),
            ) {
                if (!hasCameraPermission()) {
                    PermissionPrompt(
                        onGrantCamera = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    if (scanEnabled && pendingPayload == null) {
                        PairingQrScanner(
                            modifier = Modifier.matchParentSize(),
                            onDecodedPayload = { raw ->
                                errorMessage = null
                                when (val result = validatePairingQrCode(raw)) {
                                    is QrPairingValidationResult.Success -> {
                                        pendingPayload = result.payload
                                        scanEnabled = false
                                        runConnect(result.payload)
                                    }
                                    is QrPairingValidationResult.ScanError -> {
                                        errorMessage = result.message
                                    }
                                    is QrPairingValidationResult.BridgeUpdateRequired -> {
                                        scanEnabled = false
                                        bridgeUpdateTitle = result.title
                                        bridgeUpdateMessage = result.message
                                        bridgeUpdateCommand = result.command
                                        bridgeUpdateVisible = true
                                    }
                                }
                            },
                        )
                    }
                    QrScannerOverlay(
                        connecting = connecting,
                        statusMessage = statusMessage,
                        errorMessage = errorMessage,
                        onScanAgain = ::resetScanner,
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            Image(
                painter = painterResource(R.drawable.remodex_icon),
                contentDescription = null,
                modifier = Modifier.size(92.dp),
            )
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
            resetScanner()
        },
        onRetry = {
            bridgeUpdateVisible = false
            bridgeUpdateCommand = null
            resetScanner()
        },
        onScanNewQr = {
            bridgeUpdateVisible = false
            bridgeUpdateCommand = null
            resetScanner()
        },
    )
}

@Composable
private fun QrScannerHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 1.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_navigate_back),
                    tint = Color(0xFF151515),
                )
            }
        }
        Text(
            text = stringResource(R.string.qr_scanner_title),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = Color(0xFF151515),
        )
        Spacer(modifier = Modifier.size(52.dp))
    }
}

@Composable
private fun PermissionPrompt(
    onGrantCamera: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Camera access is needed to scan.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onGrantCamera) {
            Text(stringResource(R.string.qr_scanner_grant_camera))
        }
    }
}

@Composable
private fun QrScannerOverlay(
    connecting: Boolean,
    statusMessage: String?,
    errorMessage: String?,
    onScanAgain: () -> Unit,
) {
    val scanSize = 178.dp
    val scanRadius = 22.dp

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.08f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.34f),
                        ),
                    ),
                ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scanSizePx = scanSize.toPx()
            val scanRadiusPx = scanRadius.toPx()
            val left = (size.width - scanSizePx) / 2f
            val top = (size.height - scanSizePx) / 2f
            val dimPath =
                Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = left,
                            top = top,
                            right = left + scanSizePx,
                            bottom = top + scanSizePx,
                            radiusX = scanRadiusPx,
                            radiusY = scanRadiusPx,
                        ),
                    )
                }
            drawPath(dimPath, Color.Black.copy(alpha = 0.42f))
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(scanSize)
                    .clip(RoundedCornerShape(scanRadius))
                    .border(2.dp, Color.White.copy(alpha = 0.92f), RoundedCornerShape(scanRadius)),
        ) {
            if (connecting) {
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .size(42.dp),
                    color = Color.White,
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = when {
                    connecting -> "Connecting"
                    errorMessage != null -> "Scan failed"
                    statusMessage != null -> "Code found"
                    else -> "Scan Code"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Text(
                text = errorMessage ?: "Scan the pairing QR from your desktop bridge",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.76f),
                textAlign = TextAlign.Center,
            )
            if (errorMessage != null) {
                Button(
                    onClick = onScanAgain,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.qr_scan_again))
                }
            }
        }
    }
}
