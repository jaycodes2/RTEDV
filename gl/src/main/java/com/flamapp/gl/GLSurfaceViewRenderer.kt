package com.flamapp.gl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.flamapp.jni.NativeProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Custom Renderer that implements the GLSurfaceView.Renderer interface.
 * Handles OpenGL context, shader compilation, and drawing the processed frame.
 */
class GLSurfaceViewRenderer : GLSurfaceView.Renderer {

    private val TAG = "GLRenderer"
    private var matAddress: Long = 0L // Address of the processed OpenCV Mat
    private var textureId = 0
    private var programHandle = 0
    private var positionHandle = 0
    private var textureCoordHandle = 0
    private var textureUniformHandle = 0

    // Instance of the JNI bridge
    private val nativeProcessor = NativeProcessor()

    // --- Shaders (GLSL) ---

    // Vertex Shader: Passes position and texture coordinates to the fragment shader.
    private val vertexShaderCode =
        """
        uniform mat4 uMVPMatrix;
        attribute vec4 aPosition;
        attribute vec2 aTexCoordinate;
        varying vec2 vTexCoordinate;
        void main() {
            vTexCoordinate = aTexCoordinate;
            gl_Position = aPosition;
        }
        """

    // Fragment Shader: Samples the texture (processed frame) and applies a simple color conversion.
    private val fragmentShaderCode =
        """
    precision mediump float;
    uniform sampler2D uTexture;
    varying vec2 vTexCoordinate;
    
    void main() {
        // Sample the texture.
        vec4 color = texture2D(uTexture, vTexCoordinate);
        
        // Use the red channel (R) as the luminance value, 
        // and explicitly set R, G, and B to that value.
        // This ensures the monochromatic Canny output is correctly displayed as a white image.
        float luminance = color.r; 
        gl_FragColor = vec4(luminance, luminance, luminance, 1.0); 
    }
    """

    // Geometry data (a simple quad to draw the texture onto)
    private val quadVertices = floatArrayOf(
        -1.0f, -1.0f, // Bottom Left
        1.0f, -1.0f, // Bottom Right
        -1.0f,  1.0f, // Top Left
        1.0f,  1.0f  // Top Right
    )

    // Texture coordinates (to map the image onto the quad)
    private val textureCoords = floatArrayOf(
        0.0f, 1.0f, // Bottom Left
        1.0f, 1.0f, // Bottom Right
        0.0f, 0.0f, // Top Left
        1.0f, 0.0f  // Top Right
    )

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var textureBuffer: FloatBuffer

    init {
        // Initialize buffers
        vertexBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuffer.put(quadVertices).position(0)
        textureBuffer = ByteBuffer.allocateDirect(textureCoords.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        textureBuffer.put(textureCoords).position(0)
    }

    // --- Public Method to Receive Data ---
    fun updateMatAddress(newAddress: Long) {
        matAddress = newAddress
    }

    // --- Renderer Lifecycle Methods ---

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.2f, 0.2f, 0.4f, 1.0f) // Dark Blue background

        // 1. Create Texture
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        // 2. Initialize and compile Shaders
        programHandle = createProgram(vertexShaderCode, fragmentShaderCode)
        if (programHandle == 0) {
            Log.e(TAG, "Could not create program.")
            return
        }

        // 3. Get handle locations for attributes and uniforms
        positionHandle = GLES20.glGetAttribLocation(programHandle, "aPosition")
        textureCoordHandle = GLES20.glGetAttribLocation(programHandle, "aTexCoordinate")
        textureUniformHandle = GLES20.glGetUniformLocation(programHandle, "uTexture")

        Log.i(TAG, "OpenGL Surface created. Program ID: $programHandle, Texture ID: $textureId")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GL10.GL_COLOR_BUFFER_BIT or GL10.GL_DEPTH_BUFFER_BIT)

        if (matAddress != 0L && programHandle != 0) {

            // 1. Activate the shader program
            GLES20.glUseProgram(programHandle)

            // 2. CRITICAL STEP: Transfer processed OpenCV Mat to the GL Texture
            // C++ function reads Mat at matAddress and updates textureId
            nativeProcessor.updateGLTexture(matAddress, textureId)

            // 3. Bind Vertices
            vertexBuffer.position(0)
            GLES20.glVertexAttribPointer(
                positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer
            )
            GLES20.glEnableVertexAttribArray(positionHandle)

            // 4. Bind Texture Coordinates
            textureBuffer.position(0)
            GLES20.glVertexAttribPointer(
                textureCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer
            )
            GLES20.glEnableVertexAttribArray(textureCoordHandle)

            // 5. Activate and bind the texture (unit 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glUniform1i(textureUniformHandle, 0) // Set the texture sampler to use Texture Unit 0

            // 6. Draw the quad
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            // 7. Cleanup
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(textureCoordHandle)
            GLES20.glUseProgram(0)
        }
    }

    // --- Shader Utility Functions ---

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Shader compilation failed: " + GLES20.glGetShaderInfoLog(shader))
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)

        if (vertexShader == 0 || fragmentShader == 0) return 0

        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Program linking failed: " + GLES20.glGetProgramInfoLog(program))
            GLES20.glDeleteProgram(program)
            return 0
        }

        // Clean up shaders after linking
        GLES20.glDetachShader(program, vertexShader)
        GLES20.glDetachShader(program, fragmentShader)
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        return program
    }
}