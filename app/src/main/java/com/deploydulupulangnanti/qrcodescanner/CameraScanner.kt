package com.deploydulupulangnanti.qrcodescanner

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

@SuppressLint("ClickableViewAccessibility")
@Composable
fun CameraScanner(torch: Boolean, onResult: (String, String) -> Unit, onError: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoom by remember { mutableFloatStateOf(0f) }
    val resultCallback by rememberUpdatedState(onResult)
    val errorCallback by rememberUpdatedState(onError)
    DisposableEffect(owner) {
        val executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var analysis: ImageAnalysis? = null
        var preview: Preview? = null
        future.addListener({
            if (!disposed) try {
                provider = future.get()
                preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val reader = BarcodeEngine.reader()
                var delivered = false
                var lastFrame = 0L
                analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888).build().also { useCase ->
                    useCase.setAnalyzer(executor) { image ->
                        try {
                            val now = System.currentTimeMillis()
                            if (!delivered && now - lastFrame > 180) {
                                lastFrame = now
                                val plane = image.planes[0]
                                val buffer = plane.buffer
                                val bytes = ByteArray(image.width * image.height)
                                for (row in 0 until image.height) {
                                    for (col in 0 until image.width) bytes[row * image.width + col] = buffer.get(row * plane.rowStride + col * plane.pixelStride)
                                }
                                val source = PlanarYUVLuminanceSource(bytes, image.width, image.height, 0, 0, image.width, image.height, false)
                                var found = runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))) }.getOrNull()
                                if (found == null) {
                                    val rotated = FrameRotation.clockwise(bytes, image.width, image.height)
                                    val rotatedSource = PlanarYUVLuminanceSource(rotated, image.height, image.width, 0, 0, image.height, image.width, false)
                                    found = runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(rotatedSource))) }.getOrNull()
                                }
                                reader.reset()
                                if (found != null && !found.text.isNullOrBlank()) {
                                    delivered = true
                                    val code = found
                                    ContextCompat.getMainExecutor(context).execute { if (!disposed) resultCallback(code.text, code.barcodeFormat.name) }
                                }
                            }
                        } catch (_: Exception) { /* An undecodable frame is expected. */ }
                        finally { image.close() }
                    }
                }
                val cameraProvider = provider!!
                val selector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.Builder().build()
                camera = cameraProvider.bindToLifecycle(owner, selector, preview!!, analysis!!)
                previewView.setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                        camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                    }; true
                }
            } catch (_: Exception) { errorCallback("Camera unavailable. You can still scan a saved image.") }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            analysis?.clearAnalyzer()
            val cases = listOfNotNull(preview, analysis).toTypedArray()
            if (cases.isNotEmpty()) provider?.unbind(*cases)
            executor.shutdown()
        }
    }
    LaunchedEffect(torch, camera) {
        camera?.let { if (it.cameraInfo.hasFlashUnit()) it.cameraControl.enableTorch(torch) else if (torch) errorCallback("This camera has no flashlight.") }
    }
    LaunchedEffect(zoom, camera) { camera?.cameraControl?.setLinearZoom(zoom) }
    Box(modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        if (camera != null) Slider(value = zoom, onValueChange = { zoom = it }, modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(horizontal = 48.dp, vertical = 8.dp))
    }
}
