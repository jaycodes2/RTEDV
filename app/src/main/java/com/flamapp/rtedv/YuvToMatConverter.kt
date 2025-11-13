package com.flamapp.rtedv

import androidx.camera.core.ImageProxy
import com.flamapp.jni.NativeProcessor
import java.nio.ByteBuffer

/**
 * Utility class to convert CameraX ImageProxy (YUV format) into an OpenCV Mat,
 * process it using the NativeProcessor, and manage memory addresses.
 */
object YuvToMatConverter {

    private val nativeProcessor = NativeProcessor()

    /**
     * Converts a YUV ImageProxy to an OpenCV Mat object address (long) via JNI.
     * This relies on a new native function to handle the YUV->Mat conversion efficiently.
     * @param imageProxy The frame provided by CameraX.
     * @param matAddress The current address of the Mat buffer (0 on first call).
     * @return The address of the Mat containing the current frame data.
     */
    fun imageProxyToMatAddress(imageProxy: ImageProxy, matAddress: Long): Long {

        // 1. Extract necessary YUV plane buffers
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        // 2. Pass the plane data to the dedicated C++ conversion function
        // This function will allocate/reuse Mat and perform YUV->RGBA conversion.
        return nativeProcessor.yuv420ToMat(
            imageProxy.width,
            imageProxy.height,
            yBuffer,
            uBuffer,
            vBuffer,
            imageProxy.planes[0].pixelStride,
            imageProxy.planes[1].pixelStride,
            imageProxy.planes[2].pixelStride,
            imageProxy.planes[0].rowStride,
            imageProxy.planes[1].rowStride,
            imageProxy.planes[2].rowStride,
            matAddress
        )
    }
}
