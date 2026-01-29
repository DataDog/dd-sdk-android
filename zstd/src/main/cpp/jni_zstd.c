#ifndef ZSTD_STATIC_LINKING_ONLY
#define ZSTD_STATIC_LINKING_ONLY
#endif
#include <jni.h>
#include <zstd.h>
#include <zstd_errors.h>
#include <stdint.h>

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    isError
 * Signature: (J)I
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
 * Method:    loadDictCompress
 * Signature: (J[BI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_loadDictCompress
  (JNIEnv *env, jclass obj, jlong stream, jbyteArray dict, jint dict_size) {
    if (dict == NULL) return -ZSTD_error_dictionary_wrong;
    size_t size = -ZSTD_error_memory_allocation;
    void *dict_buff = (*env)->GetPrimitiveArrayCritical(env, dict, NULL);
    if (dict_buff == NULL) goto E1;

    size = ZSTD_CCtx_loadDictionary((ZSTD_CCtx *)(intptr_t) stream, dict_buff, dict_size);
E1:
    (*env)->ReleasePrimitiveArrayCritical(env, dict, dict_buff, JNI_ABORT);
    return size;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    loadFastDictCompress
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_loadFastDictCompress
  (JNIEnv *env, jclass obj, jlong stream, jobject dict) {
    if (dict == NULL) return -ZSTD_error_dictionary_wrong;
    jclass dict_clazz = (*env)->GetObjectClass(env, dict);
    jfieldID compress_dict = (*env)->GetFieldID(env, dict_clazz, "nativePtr", "J");
    ZSTD_CDict* cdict = (ZSTD_CDict*)(intptr_t)(*env)->GetLongField(env, dict, compress_dict);
    if (cdict == NULL) return -ZSTD_error_dictionary_wrong;
    return ZSTD_CCtx_refCDict((ZSTD_CCtx *)(intptr_t) stream, cdict);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionChecksums
 * Signature: (JZ)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionChecksums
  (JNIEnv *env, jclass obj, jlong stream, jboolean enabled) {
    int checksum = enabled ? 1 : 0;
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_checksumFlag, checksum);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionLevel
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionLevel
  (JNIEnv *env, jclass obj, jlong stream, jint level) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_compressionLevel, level);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionLong
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionLong
  (JNIEnv *env, jclass obj, jlong stream, jint windowLog) {
    ZSTD_CCtx* cctx = (ZSTD_CCtx*)(intptr_t) stream;
    if (windowLog < ZSTD_WINDOWLOG_MIN || windowLog > ZSTD_WINDOWLOG_LIMIT_DEFAULT) {
      // disable long matching and reset to default windowLog size
      ZSTD_CCtx_setParameter(cctx, ZSTD_c_enableLongDistanceMatching, ZSTD_ps_disable);
      ZSTD_CCtx_setParameter(cctx, ZSTD_c_windowLog, 0);
    } else {
      ZSTD_CCtx_setParameter(cctx, ZSTD_c_enableLongDistanceMatching, ZSTD_ps_enable);
      ZSTD_CCtx_setParameter(cctx, ZSTD_c_windowLog, windowLog);
    }
    return 0;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionWorkers
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionWorkers
  (JNIEnv *env, jclass obj, jlong stream, jint workers) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_nbWorkers, workers);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionJobSize
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionJobSize
  (JNIEnv *env, jclass obj, jlong stream, jint jobSize) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_jobSize, jobSize);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionOverlapLog
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionOverlapLog
  (JNIEnv *env, jclass obj, jlong stream, jint overlapLog) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_overlapLog, overlapLog);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionWindowLog
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionWindowLog
  (JNIEnv *env, jclass obj, jlong stream, jint windowLog) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_windowLog, windowLog);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionHashLog
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionHashLog
  (JNIEnv *env, jclass obj, jlong stream, jint hashLog) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_hashLog, hashLog);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionChainLog
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionChainLog
  (JNIEnv *env, jclass obj, jlong stream, jint chainLog) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_chainLog, chainLog);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionSearchLog
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionSearchLog
  (JNIEnv *env, jclass obj, jlong stream, jint searchLog) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_searchLog, searchLog);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionMinMatch
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionMinMatch
  (JNIEnv *env, jclass obj, jlong stream, jint minMatch) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_minMatch, minMatch);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionTargetLength
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionTargetLength
  (JNIEnv *env, jclass obj, jlong stream, jint targetLength) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_targetLength, targetLength);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionStrategy
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionStrategy
  (JNIEnv *env, jclass obj, jlong stream, jint strategy) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_strategy, strategy);
}

JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_defaultCompressionLevel
  (JNIEnv *env, jclass obj) {
    return ZSTD_CLEVEL_DEFAULT;
}

#define JNI_ZSTD_ERROR(err, name) \
  JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_err##name \
    (JNIEnv *env, jclass obj) { \
      return ZSTD_error_##err; \
  }

JNI_ZSTD_ERROR(memory_allocation,             MemoryAllocation)
