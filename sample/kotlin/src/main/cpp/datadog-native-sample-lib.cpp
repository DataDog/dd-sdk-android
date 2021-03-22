#include <jni.h>
#include <signal.h>

void crash_with_sigsegv_signal() {
    int *pointer = nullptr;
#pragma clang diagnostic push
#pragma ide diagnostic ignored "NullDereferences"
    int t = *pointer;
#pragma clang diagnostic pop
}

#pragma clang diagnostic push
#pragma ide diagnostic ignored "hicpp-exception-baseclass"
void crash_with_sigabrt_signal() {
    throw "Unhandled Exception";
}
#pragma clang diagnostic pop

int crash_with_sigill_signal() {
    // do not return anything to simulate the SIGILL
}

extern "C" JNIEXPORT void JNICALL
Java_com_datadog_android_sample_crash_CrashFragment_simulateNdkCrash(
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