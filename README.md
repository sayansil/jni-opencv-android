[//]: # (Taking help from https://medium.com/kouosl/opencv-4-2-0-set-up-for-c-in-android-studio-dc88b0bb34b2)

## Setup opencv library

- Start the project with *native-c++* support. This creates a *cpp* directory in *main* directory of the app, containing *CMakeLists.txt* and *native-lib.cpp* by default.

- Create a JNI Folder in *main* directory of app and copy the contents of *OpenCV-android-sdk/sdk/native/libs/* into this directory.

- Add the path to opencv sdk to *gradle.properties*

```
opencvsdk=/home/user/Downloads/OpenCV-android-sdk
```

- Create a new *opencv* directory during build by adding the following lines to *settings.gradle*

```
include ':opencv'
project(':opencv').projectDir = new File(opencvsdk + '/sdk')
```

- Add cmake configs and abi-filters by adding the below lines inside `defaultConfig` in app-level *build.gradle*

```
externalNativeBuild {
        cmake {
            cppFlags "-std=c++17 -frtti -fexceptions"
            abiFilters 'x86', 'x86_64', 'armeabi-v7a', 'arm64-v8a'
            arguments "-DOpenCV_DIR=" + opencvsdk + "/sdk/native"
        }
    }
```

- Add the *opencv* directory as a dependency to the application by adding the following lines inside `dependencies` in the project-level *build.gradle*

```
implementation project(path: ':opencv')
```

- Modify the *CMakeLists.txt* file to include the opencv library by adding the following lines before existing `add_library()` section

```
include_directories(${OpenCV_DIR}/jni/include)
add_library(lib_opencv SHARED IMPORTED)
set_target_properties(lib_opencv PROPERTIES IMPORTED_LOCATION ${OpenCV_DIR}/libs/${ANDROID_ABI}/libopencv_java4.so)
```

- Add `lib_opencv` to the `target_link_libraries()` section.