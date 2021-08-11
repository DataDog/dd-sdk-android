#include <jni.h>

void crash_with_sigsegv_signal() {
    int *pointer = nullptr;
#pragma clang diagnostic push
#pragma ide diagnostic ignored "NullDereferences"
    int t = *pointer;
#pragma clang diagnostic pop
}


extern "C" JNIEXPORT void JNICALL
Java_com_datadog_android_nightly_services_NdkCrashService_simulateNdkCrash(
        JNIEnv *env,
        jobject thiz) {
    crash_with_sigsegv_signal();
}


