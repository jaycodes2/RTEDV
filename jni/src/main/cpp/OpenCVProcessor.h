#ifndef REALTIMEVIDEOPROCESSOR_OPENCVPROCESSOR_H
#define REALTIMEVIDEOPROCESSOR_OPENCVPROCESSOR_H

#include <jni.h>
#include <string>
#include <GLES2/gl2.h>

namespace cv {
    class Mat;
}

namespace processor {

    // Core processing functions
    void applyCannyEdge(cv::Mat* matPtr);
    void applyGrayscale(cv::Mat* matPtr);

    // GL Transfer function (Uses GLuint for texture ID)
    void transferMatToGLTexture(cv::Mat* matPtr, GLuint textureId);

    // YUV Conversion function (CRITICAL FIX: Added JNIEnv* argument)
    jlong yuv420ToMat(
            JNIEnv* env, // <-- CRITICAL: Required to access ByteBuffer memory
            int width, int height,
            jobject yBuffer, jobject uBuffer, jobject vBuffer,
            int yPixelStride, int uPixelStride, int vPixelStride,
            int yRowStride, int uRowStride, int vRowStride,
            jlong matAddr
    );

} // namespace processor

#endif // REALTIMEVIDEOPROCESSOR_OPENCVPROCESSOR_H