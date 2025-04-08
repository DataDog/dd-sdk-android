#include <jni.h>
#include "tracer_lib.h"

// All JNI functions need C linkage
extern "C" {

/*
 * Class:     com_example_tracer_TracerNative
 * Method:    createTracer
 * Signature: ()J
 *
 * Creates a new tracer instance and returns its pointer as a jlong.
 */
JNIEXPORT jlong JNICALL
Java_com_datadog_android_ndk_tracer_NdkTracer_nativeCreateTracer(JNIEnv *env, jobject instance) {
    Tracer *tracer = tracer_new();
    return reinterpret_cast<jlong>(tracer);
}

/*
 * Class:     com_example_tracer_TracerNative
 * Method:    freeTracer
 * Signature: (J)V
 *
 * Frees the tracer instance pointed to by the given jlong.
 */
JNIEXPORT void JNICALL
Java_com_example_tracer_TracerNative_freeTracer(JNIEnv *env, jobject instance, jlong tracerHandle) {
    Tracer *tracer = reinterpret_cast<Tracer *>(tracerHandle);
    tracer_free(tracer);
}

/*
 * Class:     com_example_tracer_TracerNative
 * Method:    startSpan
 * Signature: (JLjava/lang/String;J)J
 *
 * Starts a new span with the given name and parent span ID.
 * Returns the new span ID.
 */
JNIEXPORT jstring JNICALL Java_com_datadog_android_ndk_tracer_NdkTracer_nativeStartSpan(JNIEnv *env, jobject instance,
                                                                                        jlong tracerHandle,
                                                                                        jstring name,
                                                                                        jstring parentSpanId) {
    // Convert the Java string to a C string.
    const char *nativeName = env->GetStringUTFChars(name, nullptr);
    // Convert the parent span ID to a C string.
    const char *parentSpanIdAsStr = nullptr;
    if (parentSpanId != nullptr) {
        parentSpanIdAsStr = env->GetStringUTFChars(parentSpanId, nullptr);
    }
    const char *spanId = tracer_start_span(reinterpret_cast<Tracer *>(tracerHandle), nativeName, parentSpanIdAsStr);
    // Release the Java string.
    env->ReleaseStringUTFChars(name, nativeName);
    if(parentSpanId != nullptr) {
        env->ReleaseStringUTFChars(parentSpanId, parentSpanIdAsStr);
    }
    // Convert the C string to a Java string and return it.

    return env->NewStringUTF(spanId);
}

/*
 * Class:     com_example_tracer_TracerNative
 * Method:    endSpan
 * Signature: (JJ)Z
 *
 * Ends the span specified by span ID.
 * Returns JNI_TRUE if successful or JNI_FALSE otherwise.
 */
JNIEXPORT jboolean JNICALL Java_com_datadog_android_ndk_tracer_NdkTracer_nativeFinishSpan(JNIEnv *env, jobject instance,
                                                                                          jlong tracerHandle,
                                                                                          jstring spanId) {
    const char *spanIdStr = env->GetStringUTFChars(spanId, nullptr);
    bool result = tracer_end_span(reinterpret_cast<Tracer *>(tracerHandle), spanIdStr);
    env->ReleaseStringUTFChars(spanId, spanIdStr);
    return result ? JNI_TRUE : JNI_FALSE;
}

///*
// * Class:     com_example_tracer_TracerNative
// * Method:    setAttribute
// * Signature: (JJLjava/lang/String;Ljava/lang/String;)Z
// *
// * Sets an attribute on the given span.
// * Returns JNI_TRUE if successful or JNI_FALSE otherwise.
// */
//JNIEXPORT jboolean JNICALL Java_com_example_tracer_TracerNative_setAttribute(JNIEnv* env, jobject instance,
//                                                                             jlong tracerHandle,
//                                                                             jstring spanId,
//                                                                             jstring key,
//                                                                             jstring value) {
//    const char* nativeKey = env->GetStringUTFChars(key, nullptr);
//    const char* nativeValue = env->GetStringUTFChars(value, nullptr);
//    const char* spanIdStr = env->GetStringUTFChars(spanId, nullptr);
//    bool result = tracer_set_attribute(reinterpret_cast<Tracer*>(tracerHandle), spanIdStr,
//                                       nativeKey, nativeValue);
//    env->ReleaseStringUTFChars(key, nativeKey);
//    env->ReleaseStringUTFChars(value, nativeValue);
//    return result ? JNI_TRUE : JNI_FALSE;
//}

///*
// * Class:     com_example_tracer_TracerNative
// * Method:    getActiveSpan
// * Signature: (J)J
// *
// * Gets the current active span ID.
// */
//JNIEXPORT jlong JNICALL Java_com_example_tracer_TracerNative_getActiveSpan(JNIEnv* env, jobject instance,
//                                                                           jlong tracerHandle) {
//    SpanId spanId = tracer_get_active_span(reinterpret_cast<const Tracer*>(tracerHandle));
//    return static_cast<jlong>(spanId);
//}

} // extern "C"
