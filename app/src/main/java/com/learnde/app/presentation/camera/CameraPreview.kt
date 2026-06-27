// Путь: app/src/main/java/com/learnde/app/presentation/camera/CameraPreview.kt
package com.learnde.app.presentation.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

private const val FRAME_INTERVAL_MS = 1000L   // ≤ 1 FPS — лимит Live API
private const val MAX_SIDE = 768              // рекомендация Google для кадров
private const val JPEG_QUALITY = 70

/**
 * Обёртка: запрашивает разрешение CAMERA, показывает живое превью и отдаёт
 * JPEG-кадры (не чаще 1 в секунду) наружу через [onFrame].
 * Камера привязана к жизненному циклу: уходит в фон → камера сама отключается.
 */
@Composable
fun CameraLayer(
    active: Boolean,
    onFrame: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!active) return
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    LaunchedEffect(active) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    if (granted) {
        CameraPreview(onFrame = onFrame, modifier = modifier)
    } else {
        Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Нужен доступ к камере", color = Color.White)
        }
    }
}

@Composable
private fun CameraPreview(
    onFrame: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val lastSent = remember { AtomicLong(0L) }
    val callback by rememberUpdatedState(onFrame)

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = runCatching { providerFuture.get() }.getOrNull()
            if (provider != null) {
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { ia ->
                        ia.setAnalyzer(executor) { proxy ->
                            val now = System.currentTimeMillis()
                            if (now - lastSent.get() >= FRAME_INTERVAL_MS) {
                                lastSent.set(now)
                                runCatching {
                                    val bmp = proxy.toBitmap()
                                        .rotated(proxy.imageInfo.rotationDegrees)
                                        .downscaled(MAX_SIDE)
                                    val out = ByteArrayOutputStream()
                                    bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                                    callback(out.toByteArray())
                                }
                            }
                            proxy.close()
                        }
                    }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { providerFuture.get().unbindAll() }
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier.fillMaxSize())
}

private fun Bitmap.rotated(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val m = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
}

private fun Bitmap.downscaled(maxSide: Int): Bitmap {
    val longest = maxOf(width, height)
    if (longest <= maxSide) return this
    val scale = maxSide.toFloat() / longest
    return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
}
