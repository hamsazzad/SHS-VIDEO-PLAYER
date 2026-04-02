package dev.anilbeesetti.nextplayer.ui

import android.Manifest
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.guava.await

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QrScannerDialog(
    onResult: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // ── Bug fix: gate camera binding on explicit CAMERA permission ────────────
    // Previously, QrCameraPreview tried to bind the camera unconditionally.
    // On devices where CAMERA permission was not yet granted, bindToLifecycle()
    // threw SecurityException (caught silently) and showed a black "holding" screen.
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.75f)
                .background(Color.Black, shape = RoundedCornerShape(16.dp)),
        ) {
            when (val status = cameraPermission.status) {
                is PermissionStatus.Granted -> {
                    // Permission granted — safe to initialise CameraX
                    QrCameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        onQrScanned = { result ->
                            onResult(result)
                            onDismiss()
                        },
                    )
                }
                is PermissionStatus.Denied -> {
                    // Show a clear explanation and a one-tap request button
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            NextIcons.Camera,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .padding(bottom = 16.dp)
                                .then(Modifier.fillMaxWidth(0.25f)),
                        )
                        Text(
                            text = if (status.shouldShowRationale)
                                "Camera access is needed to scan QR codes. Tap below to grant it."
                            else
                                "Camera permission is required to scan QR codes.",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        androidx.compose.foundation.layout.Spacer(
                            modifier = Modifier.height(16.dp),
                        )
                        Button(onClick = { cameraPermission.launchPermissionRequest() }) {
                            Text("Grant Camera Permission")
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (cameraPermission.status is PermissionStatus.Granted) {
                    Text(
                        text = "Point camera at QR code",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Icon(NextIcons.Close, contentDescription = "Close scanner", tint = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun QrCameraPreview(
    modifier: Modifier = Modifier,
    onQrScanned: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanned = remember { AtomicBoolean(false) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            cameraProvider?.unbindAll()
        }
    }

    // Re-bind camera whenever the previewView surface changes OR the lifecycle
    // owner changes (screen rotation, Dialog recomposition)
    LaunchedEffect(lifecycleOwner, previewView) {
        val pv = previewView ?: return@LaunchedEffect
        try {
            val provider = ProcessCameraProvider.getInstance(context).await()
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = pv.surfaceProvider
            }

            val barcodeScanner = BarcodeScanning.getClient()
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null && !scanned.get()) {
                    val image = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees,
                    )
                    barcodeScanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            val qrValue = barcodes
                                .firstOrNull { barcode -> barcode.format == Barcode.FORMAT_QR_CODE }
                                ?.rawValue
                            if (qrValue != null && scanned.compareAndSet(false, true)) {
                                onQrScanned(qrValue)
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                } else {
                    imageProxy.close()
                }
            }

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis,
            )
        } catch (exc: SecurityException) {
            // CAMERA permission revoked between the permission check and bindToLifecycle call
            Log.e("QrScanner", "Camera permission denied at binding time", exc)
        } catch (exc: Exception) {
            Log.e("QrScanner", "Camera binding failed: ${exc.javaClass.simpleName}", exc)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }.also { pv -> previewView = pv }
        },
    )
}
