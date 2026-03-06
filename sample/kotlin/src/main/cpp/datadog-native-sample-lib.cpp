#include <jni.h>
#include <signal.h>

extern "C" JNIEXPORT void JNICALL
Java_com_datadog_android_sample_crash_CrashFragment_simulateNdkCrash(
    JNIEnv *env,
    jobject thiz,
    jint signal_type) {

    raise(signal_type);
}
