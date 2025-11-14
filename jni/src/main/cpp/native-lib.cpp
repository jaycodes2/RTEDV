#include <jni.h>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <android/log.h>
#include <chrono>

using namespace cv;

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#define TAG "RTED_JNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Global variables for performance tracking
long long frameCount = 0;
long long totalProcessingTime = 0;
std::chrono::steady_clock::time_point lastFpsTime = std::chrono::steady_clock::now();

extern "C" JNIEXPORT void JNICALL
Java_com_flamapp_jni_NativeProcessor_processFrame(
        JNIEnv *env,
        jclass clazz,
        jlong matAddrRgba) {

    auto startTime = std::chrono::steady_clock::now();

    Mat& inputMat = *(Mat*)matAddrRgba;

    if (inputMat.empty() || inputMat.channels() != 4) {
        LOGE("Input Mat is empty or wrong format (expected RGBA).");
        return;
    }

    try {
        Mat grayMat;
        cvtColor(inputMat, grayMat, COLOR_RGBA2GRAY);

        Mat detectedEdges;
        Canny(grayMat, detectedEdges, 50, 150, 3);

        cvtColor(detectedEdges, inputMat, COLOR_GRAY2RGBA);

    } catch (const cv::Exception& e) {
        LOGE("OpenCV Error: %s", e.what());
    }

    auto endTime = std::chrono::steady_clock::now();
    auto processingTime = std::chrono::duration_cast<std::chrono::microseconds>(endTime - startTime).count();

    // Update performance stats
    frameCount++;
    totalProcessingTime += processingTime;
}

extern "C" JNIEXPORT void JNICALL
Java_com_flamapp_jni_NativeProcessor_updateGLTexture(
        JNIEnv *env,
        jclass clazz,
        jlong matAddrRgba,
        jint textureId) {

    Mat& inputMat = *(Mat*)matAddrRgba;

    if (inputMat.empty()) {
        LOGE("Input Mat is empty for texture update.");
        return;
    }

    glBindTexture(GL_TEXTURE_2D, textureId);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, inputMat.cols, inputMat.rows, 0,
                 GL_RGBA, GL_UNSIGNED_BYTE, inputMat.data);

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_flamapp_jni_NativeProcessor_getFrameCount(
        JNIEnv *env,
        jclass clazz) {
    return frameCount;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_flamapp_jni_NativeProcessor_getTotalProcessingTime(
        JNIEnv *env,
        jclass clazz) {
    return totalProcessingTime;
}

extern "C" JNIEXPORT void JNICALL
Java_com_flamapp_jni_NativeProcessor_resetStats(
        JNIEnv *env,
        jclass clazz) {
    frameCount = 0;
    totalProcessingTime = 0;
    lastFpsTime = std::chrono::steady_clock::now();
}