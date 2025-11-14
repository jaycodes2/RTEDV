package com.flamapp.rtedv

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.flamapp.gl.GlCameraView
import com.flamapp.jni.NativeProcessor
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var viewFinder: GlCameraView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var modeToggleButton: FloatingActionButton
    private var imageAnalysis: ImageAnalysis? = null

    // Stats UI
    private lateinit var fpsText: TextView
    private lateinit var processingTimeText: TextView
    private lateinit var modeText: TextView
    private lateinit var frameCountText: TextView

    // Performance tracking
    private var lastFpsTime = System.currentTimeMillis()
    private var fps = 0.0
    private val statsHandler = Handler(Looper.getMainLooper())
    private val statsUpdateInterval = 1000L // Update stats every second

    // Mat objects (reused across frames for efficiency)
    private var yuvMat: Mat? = null
    private var rgbaMat: Mat? = null

    // Camera modes
    private enum class CameraMode {
        NORMAL,
        EDGE_DETECTION
    }

    private var currentMode = CameraMode.EDGE_DETECTION

    // Constants
    private val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)
    private val REQUEST_CODE_PERMISSIONS = 10
    private val TAG = "RTED_APP"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize OpenCV before any other operations
        initOpenCV()

        viewFinder = findViewById(R.id.viewFinder)
        modeToggleButton = findViewById(R.id.modeToggleButton)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize stats views
        initStatsViews()

        // Setup mode toggle button
        setupModeToggle()

        // Start stats updater
        startStatsUpdater()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun initStatsViews() {
        // Inflate stats overlay
        val statsOverlay = layoutInflater.inflate(R.layout.stats_overlay, null) as LinearLayout
        addContentView(statsOverlay, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(16, 16, 16, 16)
        })

        fpsText = statsOverlay.findViewById(R.id.fpsText)
        processingTimeText = statsOverlay.findViewById(R.id.processingTimeText)
        modeText = statsOverlay.findViewById(R.id.modeText)
        frameCountText = statsOverlay.findViewById(R.id.frameCountText)
    }

    private fun startStatsUpdater() {
        statsHandler.post(object : Runnable {
            override fun run() {
                updateStats()
                statsHandler.postDelayed(this, statsUpdateInterval)
            }
        })
    }

    private fun updateStats() {
        val nativeFrameCount = NativeProcessor.getFrameCount()
        val nativeProcessingTime = NativeProcessor.getTotalProcessingTime()

        // Calculate FPS
        val currentTime = System.currentTimeMillis()
        val elapsedSeconds = (currentTime - lastFpsTime) / 1000.0
        if (elapsedSeconds > 0) {
            fps = nativeFrameCount.toDouble() / elapsedSeconds
        }

        // Calculate average processing time per frame
        val avgProcessingTime = if (nativeFrameCount > 0) nativeProcessingTime / nativeFrameCount else 0

        // Get memory info
        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)
        val usedMemory = memoryInfo.dalvikPss / 1024.0 // MB

        // Update UI on main thread
        runOnUiThread {
            fpsText.text = "FPS: ${"%.1f".format(fps)}"
            processingTimeText.text = "Proc: ${"%.1f".format(avgProcessingTime / 1000.0)} ms"
            modeText.text = "Mode: ${currentMode.name}"
            frameCountText.text = "Frames: $nativeFrameCount"
        }

        // Reset stats for next interval
        NativeProcessor.resetStats()
        lastFpsTime = currentTime
    }

    private fun setupModeToggle() {
        updateModeUI()

        modeToggleButton.setOnClickListener {
            currentMode = when (currentMode) {
                CameraMode.NORMAL -> CameraMode.EDGE_DETECTION
                CameraMode.EDGE_DETECTION -> CameraMode.NORMAL
            }

            updateModeUI()
            showModeToast()
            // Reset stats when mode changes
            NativeProcessor.resetStats()
            lastFpsTime = System.currentTimeMillis()
        }
    }

    private fun updateModeUI() {
        when (currentMode) {
            CameraMode.NORMAL -> {
                modeToggleButton.setImageResource(R.drawable.ic_edge_detection)
//                modeIndicator.text = "NORMAL MODE"
            }
            CameraMode.EDGE_DETECTION -> {
                modeToggleButton.setImageResource(R.drawable.ic_normal_camera)
//                modeIndicator.text = "EDGE DETECTION MODE"
            }
        }
    }

    private fun showModeToast() {
        val message = when (currentMode) {
            CameraMode.NORMAL -> "Normal Camera Mode"
            CameraMode.EDGE_DETECTION -> "Edge Detection Mode"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // --- 1. Initialization & Permissions ---

    private fun initOpenCV() {
        if (OpenCVLoader.initLocal()) {
            Log.d(TAG, "OpenCV initialization successful.")
        } else {
            Log.e(TAG, "OpenCV initialization failed!")
            Toast.makeText(this, "Failed to load OpenCV.", Toast.LENGTH_LONG).show()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission not granted.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    // --- 2. CameraX Binding and Analysis ---

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // 1. Image Analysis Use Case
            imageAnalysis?.let { cameraProvider.unbind(it) }

            imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(viewFinder.display.rotation) // ADD THIS LINE
                .build()

            // Set the frame analyzer to our custom processor
            imageAnalysis!!.setAnalyzer(cameraExecutor, FrameProcessor())

            try {
                cameraProvider.unbindAll()
                // Bind ONLY the ImageAnalysis Use Case
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, imageAnalysis
                )
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // --- 3. Real-Time Frame Processing ---

    inner class FrameProcessor : ImageAnalysis.Analyzer {

        override fun analyze(imageProxy: ImageProxy) {
            // Get the rotation relative to the device's natural orientation
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            val currentFrameMat = convertImageProxyToMat(imageProxy)

            when (currentMode) {
                CameraMode.EDGE_DETECTION -> {
                    NativeProcessor.processFrame(currentFrameMat.nativeObj)
                }
                CameraMode.NORMAL -> {
                    // Normal mode - no processing
                }
            }

            viewFinder.onFrame(currentFrameMat)
            imageProxy.close()
        }

        private fun convertImageProxyToMat(image: ImageProxy): Mat {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            if (yuvMat == null) {
                yuvMat = Mat(image.height + image.height / 2, image.width, CvType.CV_8UC1)
                rgbaMat = Mat(image.height, image.width, CvType.CV_8UC4)
            }

            yuvMat!!.put(0, 0, nv21)
            Imgproc.cvtColor(yuvMat, rgbaMat, Imgproc.COLOR_YUV2RGBA_NV21)

            return rgbaMat!!
        }
    }

    // --- 4. Cleanup ---

    override fun onDestroy() {
        super.onDestroy()
        statsHandler.removeCallbacksAndMessages(null)
        cameraExecutor.shutdown()
        rgbaMat?.release()
        yuvMat?.release()
    }
}