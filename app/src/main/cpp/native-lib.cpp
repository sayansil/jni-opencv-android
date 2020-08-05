#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <iostream>
#include <android/log.h>
#include <opencv2/opencv.hpp>

extern "C" JNIEXPORT jstring JNICALL
Java_zds_jnitest_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject) {

    cv::Mat M(cv::Size(1, 49), CV_64FC1);
    M = 10;

    if (!M.data) {
        __android_log_print(ANDROID_LOG_ERROR, "XXX", "%s", "Fail");
    }
    std::stringstream sstr;

    sstr << M ;
    std::string str = sstr.str();

    __android_log_print(ANDROID_LOG_ERROR, "XXX", "%s", str.c_str());

    return env->NewStringUTF(str.c_str());
}
