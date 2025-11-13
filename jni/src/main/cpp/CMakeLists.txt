cmake_minimum_required(VERSION 3.22)

project("opencv_processor")

# 1. FIND HEADERS (Include directory)
include_directories(include)

# 2. SOURCE FILES
set(JNI_SRC_FILE JniBridge.cpp)
set(OPENCV_SRC_FILE OpenCVProcessor.cpp)
set(OPENCV_HEADER OpenCVProcessor.h)

# --- 3. LIBRARY LINKING ---

# Find NDK's logging library
find_library(log-lib log)
# Find Android's native OpenGL ES library (CRITICAL FIX)
find_library(glesv2-lib GLESv2)

# (Existing Imported Target Setup)
set(OPENCV_SHARED_LIB_PATH
        ${CMAKE_CURRENT_SOURCE_DIR}/../libs/${CMAKE_ANDROID_ARCH_ABI}/libopencv_java4.so
)
add_library(opencv_lib SHARED IMPORTED)
set_target_properties(opencv_lib PROPERTIES
        IMPORTED_LOCATION ${OPENCV_SHARED_LIB_PATH}
)

# --- 4. BUILD YOUR CUSTOM SHARED LIBRARY ---
add_library(
        opencv_processor
        SHARED
        ${JNI_SRC_FILE}
        ${OPENCV_SRC_FILE}
        ${OPENCV_HEADER}
)

# --- 5. LINK LIBRARIES (CRITICAL FIX HERE) ---
target_link_libraries(
        opencv_processor
        ${log-lib}
        ${glesv2-lib}   # <--- NEW: Link the OpenGL ES library
        opencv_lib      # Link the imported OpenCV target
)
