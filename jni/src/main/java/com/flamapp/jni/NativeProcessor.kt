package com.flamapp.jni

import java.nio.ByteBuffer // <-- CRITICAL FIX: Add this import

/**
 * The Kotlin interface for the native C++ library ("opencv_processor").
 * This class handles loading the native library and declaring the external functions.
 */
class NativeProcessor {

    companion object {
        init {
            try {
                System.loadLibrary("opencv_processor")
            } catch (e: UnsatisfiedLinkError) {
                println("ERROR: Native library 'opencv_processor' failed to load.")
                e.printStackTrace()
            }
        }
    }

    /**
     * Calls the C++ function to process a frame (Canny Edge Detection).
     * @param matAddr The memory address (as a Long) of the OpenCV Mat object.
     * @return The memory address of the processed Mat object.
     */
    external fun processFrame(matAddr: Long): Long

    /**
     * Returns a string from the native C++ code to verify the connection.
     */
    external fun getProcessorInfo(): String

    /**
     * Transfers processed pixel data from OpenCV Mat memory to the OpenGL Texture ID.
     */
    external fun updateGLTexture(matAddr: Long, textureId: Int)

    /**
     * CRITICAL: Converts YUV data buffers from ImageProxy to an OpenCV Mat.
     * This is implemented in C++ to handle the complex memory alignment and format conversion.
     */
    external fun yuv420ToMat(
        width: Int, height: Int,
        yBuffer: ByteBuffer, uBuffer: ByteBuffer, vBuffer: ByteBuffer,
        yPixelStride: Int, uPixelStride: Int, vPixelStride: Int,
        yRowStride: Int, uRowStride: Int, vRowStride: Int,
        matAddr: Long
    ): Long
}
