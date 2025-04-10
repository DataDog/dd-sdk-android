#include <jni.h>
#include "tracer_lib.h"
#include "android/log.h"
#include <chrono>


// All JNI functions need C linkage
extern "C" {


// Global JavaVM pointer
static JavaVM *gJvm = nullptr;
static jobject gInstance = nullptr;
static jmethodID gMethodId = nullptr;


JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    gJvm = vm;
    return JNI_VERSION_1_6;  // Use the appropriate JNI version
}

// Function to retrieve a valid JNIEnv pointer
JNIEnv *getJNIEnv() {
    JNIEnv *env = nullptr;
    int status = gJvm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);

    if (status == JNI_EDETACHED) {
        // Thread is not attached, attach it
        if (gJvm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            __android_log_print(ANDROID_LOG_ERROR, "TracerNative", "Failed to attach thread");
            return nullptr;
        }
    } else if (status != JNI_OK) {
        __android_log_print(ANDROID_LOG_ERROR, "TracerNative", "Failed to get JNI environment");
        return nullptr;
    }

    return env;
}


void consume_span(const char *span) {
    // Measure execution time
    auto start = std::chrono::high_resolution_clock::now();

    // Convert the C string to a Java string
    __android_log_print(ANDROID_LOG_DEBUG, "TracerNative", "Callback called with: %s", span);

    if (gJvm == nullptr || gInstance == nullptr || gMethodId == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "TracerNative", "Callback data is not initialized with:");
        return;
    }
    // try to get the JNIEnv and attach the current calling thread to the JVM environment if not attached
    JNIEnv *env = getJNIEnv();

    if (env == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "TracerNative", "Failed to get JNI environment");
        return;
    }
    // Create a new Java string from the C string
    jstring spanAsJString = env->NewStringUTF(span);
    env->CallVoidMethod(gInstance, gMethodId, spanAsJString);
    // Clean up local references
    env->DeleteLocalRef(spanAsJString);

    // Calculate and log execution time
    auto end = std::chrono::high_resolution_clock::now();
    double duration = (double)std::chrono::duration_cast<std::chrono::nanoseconds>(end - start).count();
    double durationMs = duration / 1000000.0;
    __android_log_print(ANDROID_LOG_DEBUG, "TracerNative", "Span consumed in %.3f ms", durationMs);
}
/*
 * Class:     com_example_tracer_TracerNative
 * Method:    createTracer
 * Signature: ()J
 *
 * Creates a new tracer instance and returns its pointer as a jlong.
 */
#pragma clang diagnostic push
#pragma ide diagnostic ignored "LocalValueEscapesScope"
JNIEXPORT jlong JNICALL
Java_com_datadog_android_ndk_tracer_NdkTracer_nativeCreateTracer(JNIEnv *env, jobject instance) {
    if (gInstance == nullptr || gMethodId == nullptr) {
        // Get the class from the object
        jclass cls = env->GetObjectClass(instance);
        if (cls == nullptr) {
            // Handle error: class not found.
            __android_log_print(ANDROID_LOG_ERROR, "TracerNative", "Class not found");
            return reinterpret_cast<jlong>(nullptr);
        }

        // Get the method ID for the instance method "javaMethod" that takes a String and returns void.
        gMethodId = env->GetMethodID(cls, "consumeSpan", "(Ljava/lang/String;)V");
        if (gMethodId == nullptr) {
            // Handle error: method not found.
            __android_log_print(ANDROID_LOG_ERROR, "TracerNative", "Method not found");
            return reinterpret_cast<jlong>(nullptr);
        }
        __android_log_print(ANDROID_LOG_DEBUG, "TracerNative", "Callback data initialized");
        gInstance = env->NewGlobalRef(instance);
    }

    Tracer *tracer = tracer_new(consume_span);
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
    if (parentSpanId != nullptr) {
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
