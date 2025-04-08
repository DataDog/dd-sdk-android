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
JNIEXPORT jlong JNICALL Java_com_datadog_android_ndk_tracer_NdkTracer_nativeCreateTracer(JNIEnv* env, jobject instance) {
    Tracer* tracer = tracer_new();
    return reinterpret_cast<jlong>(tracer);
}

/*
 * Class:     com_example_tracer_TracerNative
 * Method:    freeTracer
 * Signature: (J)V
 *
 * Frees the tracer instance pointed to by the given jlong.
 */
JNIEXPORT void JNICALL Java_com_example_tracer_TracerNative_freeTracer(JNIEnv* env, jobject instance, jlong tracerHandle) {
    Tracer* tracer = reinterpret_cast<Tracer*>(tracerHandle);
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
JNIEXPORT jlong JNICALL Java_com_example_tracer_TracerNative_startSpan(JNIEnv* env, jobject instance,
                                                                       jlong tracerHandle,
                                                                       jstring name,
                                                                       jlong parentSpanId) {
    // Convert the Java string to a C string.
    const char* nativeName = env->GetStringUTFChars(name, nullptr);
    SpanId spanId = tracer_start_span(reinterpret_cast<Tracer*>(tracerHandle), nativeName, parentSpanId);
    // Release the Java string.
    env->ReleaseStringUTFChars(name, nativeName);
    return static_cast<jlong>(spanId);
}

/*
 * Class:     com_example_tracer_TracerNative
 * Method:    endSpan
 * Signature: (JJ)Z
 *
 * Ends the span specified by span ID.
 * Returns JNI_TRUE if successful or JNI_FALSE otherwise.
 */
JNIEXPORT jboolean JNICALL Java_com_example_tracer_TracerNative_endSpan(JNIEnv* env, jobject instance,
                                                                        jlong tracerHandle,
                                                                        jlong spanId) {
    bool result = tracer_end_span(reinterpret_cast<Tracer*>(tracerHandle), static_cast<SpanId>(spanId));
    return result ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_example_tracer_TracerNative
 * Method:    setAttribute
 * Signature: (JJLjava/lang/String;Ljava/lang/String;)Z
 *
 * Sets an attribute on the given span.
 * Returns JNI_TRUE if successful or JNI_FALSE otherwise.
 */
JNIEXPORT jboolean JNICALL Java_com_example_tracer_TracerNative_setAttribute(JNIEnv* env, jobject instance,
                                                                             jlong tracerHandle,
                                                                             jlong spanId,
                                                                             jstring key,
                                                                             jstring value) {
    const char* nativeKey = env->GetStringUTFChars(key, nullptr);
    const char* nativeValue = env->GetStringUTFChars(value, nullptr);
    bool result = tracer_set_attribute(reinterpret_cast<Tracer*>(tracerHandle), static_cast<SpanId>(spanId),
                                       nativeKey, nativeValue);
    env->ReleaseStringUTFChars(key, nativeKey);
    env->ReleaseStringUTFChars(value, nativeValue);
    return result ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_example_tracer_TracerNative
 * Method:    getActiveSpan
 * Signature: (J)J
 *
 * Gets the current active span ID.
 */
JNIEXPORT jlong JNICALL Java_com_example_tracer_TracerNative_getActiveSpan(JNIEnv* env, jobject instance,
                                                                           jlong tracerHandle) {
    SpanId spanId = tracer_get_active_span(reinterpret_cast<const Tracer*>(tracerHandle));
    return static_cast<jlong>(spanId);
}

} // extern "C"
