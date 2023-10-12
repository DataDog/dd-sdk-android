/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

#include "datadog-native-lib.h"

#include <fstream>
#include <jni.h>
#include <pthread.h>
#include <sstream>
#include <string>

#include "android/log.h"
#include "crash-log.h"
#include "backtrace-handler.h"
#include "datetime-utils.h"
#include "file-utils.h"
#include "signal-monitor.h"
#include "string-utils.h"


static struct Context {
    std::string storage_dir;

    Context() : storage_dir() {}
} main_context;


static const char *LOG_TAG = "DatadogNdkCrashReporter";

static pthread_mutex_t handler_mutex = PTHREAD_MUTEX_INITIALIZER;
static const uint8_t tracking_consent_pending = 0;
static const uint8_t tracking_consent_granted = 1;
static uint8_t tracking_consent = tracking_consent_pending; // 0 - PENDING, 1 - GRANTED, 2 - NOT-GRANTED

#ifndef NDEBUG

void lockMutex() {
    pthread_mutex_lock(&handler_mutex);
}

void unlockMutex() {
    pthread_mutex_unlock(&handler_mutex);
}

#endif

void write_crash_report(int signum,
                        const char *signal_name,
                        const char *error_message,
                        const char *error_stacktrace) {
    using namespace std;
    static const std::string crash_log_filename = "crash_log";

    // sync everything
    if (tracking_consent != tracking_consent_granted) {
        return;
    }

    if(pthread_mutex_trylock(&handler_mutex) != 0){
        // There is no action to take if the mutex cannot be acquired.
        // In this case will fail to write the crash log and return here in order not
        // to block the process and create possible ANRs.
        return;
    }

    if (main_context.storage_dir.empty()) {
        __android_log_write(ANDROID_LOG_ERROR, LOG_TAG,
                            "The crash reports storage directory file path was null");
        pthread_mutex_unlock(&handler_mutex);
        return;
    }

    // create crash reporting directory if it does not exist
    if (!fileutils::create_dir_if_not_exists(main_context.storage_dir.c_str())) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                            "Was unable to create the NDK reports storage directory: %s",
                            main_context.storage_dir.c_str());
        pthread_mutex_unlock(&handler_mutex);
        return;
    }

    // serialize the log
    string file_path = main_context.storage_dir.append("/").append(crash_log_filename);
    long long timestamp = time_since_epoch();
    std::unique_ptr<CrashLog> crash_log_ptr = std::make_unique<CrashLog>(signum,
                                                                         timestamp,
                                                                         signal_name,
                                                                         error_message,
                                                                         error_stacktrace);
    string serialized_log = crash_log_ptr->serialise();

    // write the log in the crash log file
    ofstream logs_file_output_stream(file_path.c_str(),
                                     ofstream::out | ofstream::trunc);
    if (logs_file_output_stream.is_open()) {
        logs_file_output_stream << serialized_log.c_str();
    }
    logs_file_output_stream.close();
    pthread_mutex_unlock(&handler_mutex);
}

void update_main_context(JNIEnv *env,
                         jstring storage_path) {
    using namespace stringutils;
    if(pthread_mutex_trylock(&handler_mutex) != 0){
        // There is no action to take if the mutex cannot be acquired. Probably int this case
        // there is already a log writing due to a crash in progress.
        // In this case updating the context will not make sense anymore and we do not want to
        // stale the process here.
        return;
    }
    main_context.storage_dir = copy_to_string(env, storage_path);
    pthread_mutex_unlock(&handler_mutex);
}

void update_tracking_consent(jint consent) {
    tracking_consent = (uint8_t) consent;
}


/// Jni bindings
extern "C" JNIEXPORT void JNICALL
Java_com_datadog_android_ndk_internal_NdkCrashReportsFeature_registerSignalHandler(
        JNIEnv *env,
        jobject /* this */,
        jstring storage_path,
        jint consent) {

    update_main_context(env, storage_path);
    update_tracking_consent(consent);
    start_monitoring();
}


extern "C" JNIEXPORT void JNICALL
Java_com_datadog_android_ndk_internal_NdkCrashReportsFeature_unregisterSignalHandler(
        JNIEnv *env,
        jobject /* this */) {
    stop_monitoring();
}

extern "C" JNIEXPORT void JNICALL
Java_com_datadog_android_ndk_internal_NdkCrashReportsFeature_updateTrackingConsent(
        JNIEnv *env,
        jobject /* this */,
        jint consent) {
    update_tracking_consent(consent);
}