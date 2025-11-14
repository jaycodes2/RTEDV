# Real-Time Edge Detection Viewer - Android + OpenCV + OpenGL + TypeScript

## 📱 Project Overview

This project implements a real-time edge detection Android application that captures camera frames, processes them using OpenCV C++ via JNI, renders the output with OpenGL ES, and includes a TypeScript web viewer for displaying processed frames.

![Architecture Diagram](https://github.com/jaycodes2/RTEDV/blob/be1ea7082a5c71407ce1d7ae9cde44029d8bf2fb/architecture_diagram.png?raw=true)


## 🏗️ System Architecture

### Core Components Flow

```
Android Camera → JNI Bridge → OpenCV C++ Processing → OpenGL ES Rendering → TypeScript Web Viewer
```

### Detailed Workflow

1. **Android Camera Layer**
   - `TextureView`/`SurfaceTexture` for camera frame capture
   - Camera1/Camera2 API for repeating image stream
   - Frame rate monitoring (400+ FPS capability)

2. **Native Processing (C++/JNI)**
   - OpenCV C++ integration for real-time image processing
   - Canny Edge Detection and Grayscale conversion
   - Memory-optimized frame processing

3. **OpenGL ES Rendering**
   - OpenGL ES 2.0+ for hardware-accelerated rendering
   - GLSL shaders (Vertex & Fragment) for visual effects
   - `GLSurfaceView.Renderer` for display output

4. **TypeScript Web Interface**
   - Web-based frame viewer with statistics display
   - Real-time FPS and resolution monitoring
   - DOM-based frame data updates

## 🚀 Features Implemented

### ✅ Core Requirements
- **Camera Integration**: TextureView with Camera2 API
- **OpenCV Processing**: Canny Edge Detection in C++ via JNI
- **OpenGL Rendering**: Real-time texture rendering with OpenGL ES 2.0
- **TypeScript Web Viewer**: Static frame display with stats overlay
- **Modular Architecture**: Clean separation of concerns

### ✅ Bonus Features
- FPS counter and performance monitoring
- Memory usage debugging with `Debug.MemoryInfo`
- Toggle between raw feed and processed output
- OpenGL shaders for visual effects
- Proper Git commit history with meaningful messages

## 📁 Project Structure

```
app/
├── src/main/java/          # Android Java/Kotlin code
│   ├── camera/            # Camera management
│   ├── gl/               # OpenGL renderer classes
│   └── ui/               # Activity and UI components
jni/
├── CMakeLists.txt        # Native build configuration
├── native-lib.cpp       # JNI interface implementation
├── opencv-processor.cpp # OpenCV processing logic
└── opengl-renderer.cpp  # OpenGL native rendering
gl/
├── shaders/             # GLSL vertex and fragment shaders
└── renderers/          # OpenGL renderer implementations
web/
├── src/
│   ├── typescript/      # TypeScript source code
│   └── html/           # Web page structure
├── package.json        # TypeScript build configuration
└── tsconfig.json      # TypeScript compiler settings
```

## 🔧 Setup Instructions

### Prerequisites
- Android Studio Arctic Fox+
- Android NDK 21+
- OpenCV 4.5+ Android SDK
- Node.js 14+ (for TypeScript web viewer)
- TypeScript 4+

### Android Setup
1. Clone the repository
2. Open in Android Studio
3. Download NDK if not present
4. Add OpenCV Android SDK to `app/libs/`
5. Update `local.properties` with your SDK/NDK paths
6. Build and run on Android device/emulator

### OpenCV Configuration
```cmake
# CMakeLists.txt
find_package(OpenCV REQUIRED)
target_link_libraries(native-lib ${OpenCV_LIBRARIES})
```

### Web Viewer Setup
```bash
cd web
npm install
npm run build
npm start
```

## 🎯 Use Cases

### Primary Use Case: Real-time Edge Detection
1. User launches Android app
2. Camera automatically starts capturing
3. Frames are processed in real-time with Canny edge detection
4. Processed output displayed via OpenGL rendering
5. FPS and performance stats shown overlay

### Secondary Use Case: Web Debugging
1. Processed frames can be exported from Android app
2. Web viewer displays static processed images
3. Frame statistics (FPS, resolution) shown in web interface
4. Useful for debugging and demonstration purposes

## 🔄 Frame Processing Pipeline

1. **Capture**: Android Camera2 API → `ImageReader`
2. **Transfer**: JNI bridge → Native C++ context
3. **Process**: OpenCV C++ → Canny Edge Detection
4. **Convert**: Processed data → OpenGL texture
5. **Render**: OpenGL ES 2.0 → `GLSurfaceView`
6. **Display**: Final output on `TextureView`
7. **Export**: Optional frame save for web viewer

## 🛠️ Technical Implementation

### JNI Communication
```cpp
// Java to C++ frame transfer
extern "C" JNIEXPORT void JNICALL
Java_com_example_app_ProcessFrame(JNIEnv *env, jobject thiz, jbyteArray frame_data);
```

### OpenCV Processing
```cpp
// Canny edge detection implementation
cv::Mat processFrame(cv::Mat inputFrame) {
    cv::Mat gray, edges;
    cv::cvtColor(inputFrame, gray, cv::COLOR_RGBA2GRAY);
    cv::Canny(gray, edges, 50, 150);
    return edges;
}
```

### OpenGL Shaders
```glsl
// Fragment shader for texture rendering
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D uTexture;
void main() {
    gl_FragColor = texture2D(uTexture, vTexCoord);
}
```



## 🎥 Demo & Screenshots


## 🔮 Future Enhancements

- WebSocket integration for live web streaming
- Multiple filter options (Sobel, Laplacian)
- Camera parameter controls via web interface
- Advanced OpenGL effects and shaders
- Cross-platform compatibility
