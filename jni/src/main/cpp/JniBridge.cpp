#include <jni.h>
#include <android/log.h>
#include <string>
#include "opencv2/core/core.hpp"
#include "OpenCVProcessor.h"

#define LOG_TAG "JniBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// JNI function to process a frame (Canny Edge)
extern "C" JNIEXPORT jlong JNICALL
Java_com_flamapp_jni_NativeProcessor_processFrame(
        JNIEnv* env, jobject /* this */, jlong matAddr) {
    cv::Mat* inputMat = reinterpret_cast<cv::Mat*>(matAddr);
    if (inputMat) {
        processor::applyCannyEdge(inputMat);
    } else {
        LOGE("Error: Received null Mat address in processFrame.");
    }
    return matAddr;
}

// JNI function to transfer Mat data to OpenGL Texture (Fix 1: GLuint conversion)
extern "C" JNIEXPORT void JNICALL
Java_com_flamapp_jni_NativeProcessor_updateGLTexture(
        JNIEnv* env, jobject /* this */, jlong matAddr, jint textureId) {
    cv::Mat* matPtr = reinterpret_cast<cv::Mat*>(matAddr);
    if (matPtr) {
        // Use static cast to match the GLuint type in the core C++ function
        processor::transferMatToGLTexture(matPtr, static_cast<GLuint>(textureId));
    } else {
        LOGE("Error: Received null Mat address in updateGLTexture.");
    }
}

// JNI function for YUV to Mat conversion (Fix 2: Passes JNIEnv*)
extern "C" JNIEXPORT jlong JNICALL
Java_com_flamapp_jni_NativeProcessor_yuv420ToMat(
        JNIEnv* env, jobject /* this */,
        jint width, jint height,
        jobject yBuffer, jobject uBuffer, jobject vBuffer,
        jint yPixelStride, jint uPixelStride, jint vPixelStride,
        jint yRowStride, jint uRowStride, jint vRowStride,
        jlong matAddr) {

    // Pass the JNIEnv* and all arguments to the core C++ logic
    return processor::yuv420ToMat(
            env, // <-- CRITICAL: Passed to the core C++ logic
            width, height,
            yBuffer, uBuffer, vBuffer,
            yPixelStride, uPixelStride, vPixelStride,
            yRowStride, uRowStride, vRowStride,
            matAddr
    );
}

// Simple function to verify the JNI link and get OpenCV version
extern "C" JNIEXPORT jstring JNICALL
Java_com_flamapp_jni_NativeProcessor_getProcessorInfo(
        JNIEnv* env, jobject /* this */) {
    std::string info = "OpenCV Processor v1.0. OpenCV version: " + std::string(CV_VERSION);
    return env->NewStringUTF(info.c_str());
}