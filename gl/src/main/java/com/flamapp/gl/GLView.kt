package com.flamapp.gl

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.Surface
import android.graphics.PixelFormat // <-- NEW IMPORT
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.core.content.ContextCompat
import com.flamapp.jni.NativeProcessor
import java.util.concurrent.Executor

/**
 * A custom GLSurfaceView designed to host our custom renderer.
 */
class GLView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val customRenderer: GLSurfaceViewRenderer

    init {
        // 1. Specify the use of OpenGL ES 2.0 or greater
        setEGLContextClientVersion(2)

        // CRITICAL FIX 1: Configure the SurfaceHolder for transparency (RGBA 8888)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        // CRITICAL FIX 2: Set EGL configuration chooser for color depth/transparency
        setEGLConfigChooser(8, 8, 8, 8, 16, 0) // RGBA 8888 config

        // 2. Instantiate and set our custom renderer
        customRenderer = GLSurfaceViewRenderer()
        setRenderer(customRenderer)

        // 3. Render continuously for the video stream
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    /**
     * Public function for MainActivity to communicate the processed Mat address.
     */
    fun updateTexture(matAddress: Long) {
        customRenderer.updateMatAddress(matAddress)
        requestRender()
    }

    /**
     * Exposes a SurfaceProvider compatible with CameraX Preview use case.
     */
    val surfaceProvider: Preview.SurfaceProvider = Preview.SurfaceProvider { request: SurfaceRequest ->

        // Use the main thread executor (safer for system views like SurfaceView)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        val surface = holder.surface

        if (surface != null && holder.surface.isValid) {
            // Provide the GLSurfaceView's valid surface to CameraX
            request.provideSurface(surface, mainExecutor, { result: SurfaceRequest.Result ->
                // Handle result
            })
        } else {
            request.willNotProvideSurface()
        }
    }
}