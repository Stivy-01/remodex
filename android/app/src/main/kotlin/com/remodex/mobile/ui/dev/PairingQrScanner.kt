package com.remodex.mobile.ui.dev

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live camera preview with ML Kit QR decoding. Invokes [onDecodedPayload] once for the first
 * barcode whose raw value looks like JSON (starts with `{`).
 */
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun PairingQrScanner(
    modifier: Modifier = Modifier,
    onDecodedPayload: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analysisExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner =
        remember {
            BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build(),
            )
        }
    val decoded = remember { AtomicBoolean(false) }
    val previewView =
        remember {
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        }

    DisposableEffect(lifecycleOwner) {
        val released = AtomicBoolean(false)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null
        val bindRunnable =
            Runnable {
                if (released.get()) return@Runnable
                cameraProvider =
                    try {
                        providerFuture.get()
                    } catch (_: Exception) {
                        null
                    }
                val provider = cameraProvider ?: return@Runnable
                if (released.get()) {
                    provider.unbindAll()
                    return@Runnable
                }
                val preview =
                    Preview.Builder()
                        .build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis =
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    if (decoded.get()) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val mediaImage = imageProxy.image
                    if (mediaImage == null) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val input =
                        InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees,
                        )
                    barcodeScanner
                        .process(input)
                        .addOnSuccessListener { barcodes ->
                            val raw =
                                barcodes.firstNotNullOfOrNull { it.rawValue }
                                    ?.trim()
                                    .orEmpty()
                            if (raw.startsWith("{") && decoded.compareAndSet(false, true)) {
                                mainExecutor.execute { onDecodedPayload(raw) }
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                } catch (_: Exception) {
                    // In use, permission revoked, etc.
                }
            }
        providerFuture.addListener(bindRunnable, mainExecutor)
        onDispose {
            released.set(true)
            mainExecutor.execute {
                cameraProvider?.unbindAll()
                analysisExecutor.shutdown()
                barcodeScanner.close()
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.clipToBounds(),
    )
}
