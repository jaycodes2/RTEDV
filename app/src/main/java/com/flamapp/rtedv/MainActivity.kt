package com.flamapp.rtedv

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.flamapp.jni.NativeProcessor
import com.flamapp.rtedv.ui.theme.RTEDVTheme
import com.flamapp.gl.GLView // <-- Imports GLView from your GL module
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.LinearLayout
import android.widget.FrameLayout
import androidx.camera.view.PreviewView

class MainActivity : ComponentActivity() {

    private val nativeProcessor = NativeProcessor()
    private val TAG = "RTEDV_MainActivity"
    private lateinit var cameraExecutor: ExecutorService

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.i(TAG, "Camera permission granted.")
            setupCameraAndUI()
        } else {
            Log.e(TAG, "Camera permission denied. Cannot start video stream.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        testJniConnection()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            setupCameraAndUI()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun testJniConnection() {
        try {
            val info = nativeProcessor.getProcessorInfo()
            Log.i(TAG, "JNI Link SUCCESS: $info")
        } catch (e: Exception) {
            Log.e(TAG, "JNI Link FAILED: Native code could not be loaded or called.", e)
        }
    }

    private fun setupCameraAndUI() {
        setContent {
            RTEDVTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CameraPreviewScreen( // <-- Using the GL integration Composable
                        cameraProviderFuture = ProcessCameraProvider.getInstance(LocalContext.current),
                        executor = cameraExecutor,
                        lifecycleOwner = LocalLifecycleOwner.current,
                        nativeProcessor = nativeProcessor,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

// =================================================================================
// Composable UI (Uses GLView for rendering)
// =================================================================================

@Composable
fun CameraPreviewScreen(
    cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
    executor: ExecutorService,
    lifecycleOwner: LifecycleOwner,
    nativeProcessor: NativeProcessor,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val TAG = "CameraPreviewScreen"

    var matAddress: Long = 0L

    // 1. Host the OpenGL View (GLView from the 'gl' module)
    AndroidView<GLView>(
        modifier = modifier.fillMaxSize(),
        factory = {
            // Instantiate the GLView (a SurfaceView)
            GLView(context)
        },
        update = { glView: GLView -> // Explicitly specify GLView type
            // 2. Start the Camera when the GLView is available
            cameraProviderFuture.addListener({
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                // 2a. Setup Preview use case
                val preview = Preview.Builder().build()

                // CRITICAL FIX: The Preview use case MUST be bound to a surface,
                // but we must use the GLView's surface to display the filtered output.
                // We use the GLView's provided surfaceProvider here.
                preview.setSurfaceProvider(glView.surfaceProvider)

                // 2b. Setup Image Analysis use case
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(executor, ImageAnalysis.Analyzer { imageProxy: ImageProxy ->

                    // --- Frame Processing Pipeline (The core logic) ---

                    try {
                        // 1. YUV to Mat conversion (Calls C++)
                        Log.d(TAG, "Attempting YUV conversion...") // <--- NEW DIAGNOSTIC LOG
                        val inputMatAddress = YuvToMatConverter.imageProxyToMatAddress(imageProxy, matAddress)

                        // 2. OpenCV Processing (Canny Edge)
                        val processedMatAddress = nativeProcessor.processFrame(inputMatAddress)

                        // 3. CRITICAL: Update the GL View with the processed data
                        glView.updateTexture(processedMatAddress)

                        matAddress = processedMatAddress

                        Log.d(TAG, "Frame processed and sent to GL.")
                    } catch (e: Exception) {
//...
                        Log.e(TAG, "Frame analysis failed: ${e.message}")
                    } finally {
                        imageProxy.close()
                    }
                })

                // 2c. Bind the use cases to the camera
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (exc: Exception) {
                    Log.e(TAG, "Use case binding failed", exc)
                }

            }, ContextCompat.getMainExecutor(context))
        }
    )
}


