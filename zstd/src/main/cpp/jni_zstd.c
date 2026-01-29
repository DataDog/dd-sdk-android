#include <jni.h>
#include <zstd.h>
#include <zstd_errors.h>

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    isError
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_com_github_luben_zstd_Zstd_isError
  (JNIEnv *env, jclass obj, jlong code) {
    return ZSTD_isError((size_t) code) != 0;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    getErrorName
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_github_luben_zstd_Zstd_getErrorName
  (JNIEnv *env, jclass obj, jlong code) {
    const char *msg = ZSTD_getErrorName(code);
    return (*env)->NewStringUTF(env, msg);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    getErrorCode
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_getErrorCode
  (JNIEnv *env, jclass obj, jlong code) {
    return ZSTD_getErrorCode((size_t) code);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    errMemoryAllocation
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_errMemoryAllocation
  (JNIEnv *env, jclass obj) {
    return ZSTD_error_memory_allocation;
}
