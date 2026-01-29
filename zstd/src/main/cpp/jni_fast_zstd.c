#ifndef ZSTD_STATIC_LINKING_ONLY
#define ZSTD_STATIC_LINKING_ONLY
#endif
#include <jni.h>
#include <zstd.h>
#include <zstd_errors.h>
#include <stdint.h>

// They can't change in the same VM
static jfieldID compress_dict = 0;

/*
 * Class:     com_github_luben_zstd_ZstdDictCompress
 * Method:    init
 * Signature: ([BIII)V
 */
JNIEXPORT void JNICALL Java_com_github_luben_zstd_ZstdDictCompress_init
  (JNIEnv *env, jobject obj, jbyteArray dict, jint dict_offset, jint dict_size, jint level)
{
    jclass clazz = (*env)->GetObjectClass(env, obj);
    compress_dict = (*env)->GetFieldID(env, clazz, "nativePtr", "J");
    if (NULL == dict) return;
    void *dict_buff = (*env)->GetPrimitiveArrayCritical(env, dict, NULL);
    if (NULL == dict_buff) return;
    ZSTD_CDict* cdict = ZSTD_createCDict(((char *)dict_buff) + dict_offset, dict_size, level);
    (*env)->ReleasePrimitiveArrayCritical(env, dict, dict_buff, JNI_ABORT);
    if (NULL == cdict) return;
    (*env)->SetLongField(env, obj, compress_dict, (jlong)(intptr_t) cdict);
}

/*
 * Class:     com_github_luben_zstd_ZstdDictCompress
 * Method:    init
 * Signature: (Ljava/nio/ByteBuffer;IIII)V
 */
JNIEXPORT void JNICALL Java_com_github_luben_zstd_ZstdDictCompress_initDirect
  (JNIEnv *env, jobject obj, jobject dict, jint dict_offset, jint dict_size, jint level, jint byReference)
{
    jclass clazz = (*env)->GetObjectClass(env, obj);
    compress_dict = (*env)->GetFieldID(env, clazz, "nativePtr", "J");
    if (NULL == dict) return;
    void *dict_buff = (*env)->GetDirectBufferAddress(env, dict);
    if (NULL == dict_buff) return;
    ZSTD_CDict* cdict = NULL;
    if (byReference == 0) {
      cdict = ZSTD_createCDict(((char *)dict_buff) + dict_offset, dict_size, level);
    } else {
      cdict = ZSTD_createCDict_byReference(((char *)dict_buff) + dict_offset, dict_size, level);
    }
    if (NULL == cdict) return;
    (*env)->SetLongField(env, obj, compress_dict, (jlong)(intptr_t) cdict);
}

/*
 * Class:     com_github_luben_zstd_ZstdDictCompress
 * Method:    free
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_github_luben_zstd_ZstdDictCompress_free
  (JNIEnv *env, jobject obj)
{
    if (compress_dict == 0) return;
    ZSTD_CDict* cdict = (ZSTD_CDict*)(intptr_t)(*env)->GetLongField(env, obj, compress_dict);
    if (NULL == cdict) return;
    ZSTD_freeCDict(cdict);
}
