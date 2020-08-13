#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <iostream>
#include <android/log.h>
#include <opencv2/opencv.hpp>
#include <opencv2/highgui.hpp>

extern "C" JNIEXPORT jstring JNICALL
Java_zds_jnitest_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject) {


    std::string vidPath = "/storage/emulated/0/Pandu/video/";
    std::string filePath = "/storage/emulated/0/Pandu/xyz.jpg";

    cv::Mat M = cv::imread(filePath);

    if (!M.data) {
        __android_log_print(ANDROID_LOG_DEBUG, "XXX", "%s", "Fail");
    }
    std::stringstream sstr;

    sstr << M ;
    std::string str = sstr.str();

    __android_log_print(ANDROID_LOG_DEBUG, "XXX", "%s", str.c_str());

    cv::imwrite("/storage/emulated/0/Pandu/res.jpg", M);

//    cv::imshow("frame", M);
//
//    cv::VideoCapture cap(0);
//
//    int count = 0, maxcount = 0;
//
//    while(true) {
//        if (maxcount++ >= 500) break;
//        cv::Mat frame;
//        cap >> frame;
//
//        __android_log_print(ANDROID_LOG_ERROR, "XXX", "%d", maxcount);
//        if (!frame.data) continue;
//        std::string tmp = std::string("frame") + std::to_string(count) + ".jpg";
//        cv::imwrite(vidPath + tmp, frame);
//        if(count++ == 50) break;
//    }
//
//    cap.release();

    return env->NewStringUTF(str.c_str());
}

std::tuple<int, int, int> getRGB (int n) {
    auto [r, g, b] = std::make_tuple((n >> 16) & 0xff, (n >> 8) & 0xff, (n) & 0xff);
    return {r, g, b};
}

extern "C" JNIEXPORT void JNICALL
Java_zds_jnitest_MainActivity_doSomething(
        JNIEnv* env, jobject obj, jintArray arr, jint width, jint height) {
    __android_log_print(ANDROID_LOG_DEBUG, "XXX", "%s", "entered");

    std::vector<int> result (width * height);

    jsize arrDim = env->GetArrayLength(arr);
    jint *data = env->GetIntArrayElements(arr, nullptr);
    std::copy(data, data + arrDim, result.begin());

    auto [r, g, b] = getRGB(result[0]);




    __android_log_print(ANDROID_LOG_DEBUG, "XXX", "%d, %d, %d", r, g, b);

    env->ReleaseIntArrayElements(arr, data, 0);
}