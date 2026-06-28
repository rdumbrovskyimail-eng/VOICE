package com.learnde.app.presentation.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.learnde.app.ui.theme.Radius
import com.learnde.app.ui.theme.Space
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

private const val FRAME_INTERVAL_MS = 1000L
private const val MAX_SIDE = 768
private const val JPEG_QUALITY = 70

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

    var camera by remember { mutableStateOf<Camera?>(null) }
    var hasFlash by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var boundCamera: Camera? = null
        var torchObserver: Observer<Int>? = null

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
                }.getOrNull()?.let { cam ->
                    boundCamera = cam
                    camera = cam
                    hasFlash = cam.cameraInfo.hasFlashUnit()
                    val obs = Observer<Int> { st -> torchOn = (st == TorchState.ON) }
                    torchObserver = obs
                    cam.cameraInfo.torchState.observe(lifecycleOwner, obs)
                }
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { boundCamera?.cameraControl?.enableTorch(false) }
            torchObserver?.let { obs ->
                runCatching { boundCamera?.cameraInfo?.torchState?.removeObserver(obs) }
            }
            runCatching { providerFuture.get().unbindAll() }
            executor.shutdown()
            camera = null
            hasFlash = false
            torchOn = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        if (hasFlash) {
            IconButton(
                onClick = {
                    runCatching { camera?.cameraControl?.enableTorch(!torchOn) }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Space.sm)
                    .size(36.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(Color.Black.copy(alpha = 0.5f)),
            ) {
                Icon(
                    imageVector = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = if (torchOn) "Выключить фонарик" else "Включить фонарик",
                    tint = if (torchOn) Color(0xFFFDE293) else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
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