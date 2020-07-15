#include <jni.h>
#include <signal.h>

void crash_with_sigsegv_signal() {
    int *pointer = nullptr;
    int t = *pointer;
}

void crash_with_sigabrt_signal() {
    throw "Unhandled Exception";
}

int crash_with_sigill_signal() {
    // do not return anything to simulate the SIGILL
}

extern "C" JNIEXPORT void JNICALL
Java_com_datadog_android_sample_logs_LogsFragment_simulateNdkCrash(
        JNIEnv *env,
        jobject thiz,
        jint signal_type) {

    switch (signal_type) {
        case SIGSEGV:
            crash_with_sigsegv_signal();
            break;
        case SIGABRT:
            crash_with_sigabrt_signal();
            break;
        case SIGILL:
            crash_with_sigill_signal();
            break;
        default:
            crash_with_sigsegv_signal();
            break;
    }

}