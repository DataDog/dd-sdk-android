#include <jni.h>

extern "C" JNIEXPORT void JNICALL
Java_com_datadog_android_sample_logs_LogsFragment_simulateNdkCrash(
        JNIEnv *env,
        jobject /* this */) {

    int *pointer = nullptr;
    int t = *pointer;
}