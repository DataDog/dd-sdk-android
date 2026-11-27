/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

#include "datadog-ndk.h"

#include <cerrno>
#include <cstdio>
#include <fcntl.h>
#include <jni.h>
#include <pthread.h>
#include <string>
#include <unistd.h>

#include "android/log.h"
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
static uint64_t global_app_start_time_millis = 0;

// The serialized crash report is built here rather than on the stack: it is written from the
// signal handler, which runs on a small alternate signal stack, and the report (mostly the stack
// trace, up to max_stack_size) can be tens of kilobytes. Only used while holding handler_mutex.
static char serialized_report[max_serialized_report_size];

#ifndef NDEBUG

void lockMutex() {
    pthread_mutex_lock(&handler_mutex);
}

void unlockMutex() {
    pthread_mutex_unlock(&handler_mutex);
}

#endif

/**
 * Writes the crash report as JSON into the given buffer.
 * @return the length of the report, or 0 if it does not fit in the buffer (the buffer's content
 * must then not be used).
 */
size_t serialize_crash_report(char *buffer,
                              size_t buffer_size,
                              int signum,
                              uint64_t timestamp,
                              const char *signal_name,
                              const char *error_message,
                              const char *error_stacktrace) {
    static const char *json_formatter = R"({"signal":%d,"timestamp":%llu,"time_since_app_start_ms":%llu,"signal_name":"%s","message":"%s","stacktrace":"%s"})";
    const uint64_t time_since_app_start = timestamp - global_app_start_time_millis;
    const int length = snprintf(buffer,
                                buffer_size,
                                json_formatter,
                                signum,
                                static_cast<unsigned long long>(timestamp),
                                static_cast<unsigned long long>(time_since_app_start),
                                signal_name,
                                error_message,
                                error_stacktrace);
    if (length < 0 || static_cast<size_t>(length) >= buffer_size) {
        return 0;
    }
    return static_cast<size_t>(length);
}

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
    const string file_path = main_context.storage_dir + "/" + crash_log_filename;
    const uint64_t timestamp = time_since_epoch();
    const size_t serialized_length = serialize_crash_report(serialized_report,
                                                            sizeof(serialized_report),
                                                            signum,
                                                            timestamp,
                                                            signal_name,
                                                            error_message,
                                                            error_stacktrace);
    if (serialized_length == 0) {
        __android_log_write(ANDROID_LOG_ERROR, LOG_TAG,
                            "The crash report does not fit in its buffer");
        pthread_mutex_unlock(&handler_mutex);
        return;
    }

    // Atomic write+fsync via temp file: avoids torn/zero-filled crash logs when the
    // process dies between buffer flush and disk flush.
    const std::string tmp_path = file_path + ".tmp";
    const int fd = ::open(tmp_path.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd >= 0) {
        const char *data = serialized_report;
        size_t remaining = serialized_length;
        bool write_ok = true;
        while (remaining > 0) {
            const ssize_t written = ::write(fd, data, remaining);
            if (written < 0) {
                if (errno == EINTR) continue;
                write_ok = false;
                break;
            }
            data += written;
            remaining -= static_cast<size_t>(written);
        }
        if (write_ok) {
            ::fsync(fd);
        }
        ::close(fd);
        if (write_ok) {
            ::rename(tmp_path.c_str(), file_path.c_str());
        } else {
            ::unlink(tmp_path.c_str());
        }
    }
    pthread_mutex_unlock(&handler_mutex);
}

void update_main_context(JNIEnv *env,
                         jstring storage_path) {
    using namespace stringutils;
    if(pthread_mutex_trylock(&handler_mutex) != 0){
        // There is no action to take if the mutex cannot be acquired. Probably int this case
        // there is already a log writing due to a crash in progress.
        // In this case updating the context will not make sense anymore, and we do not want to
        // stale the process here.
        return;
    }
    main_context.storage_dir = copy_to_string(env, storage_path);
    pthread_mutex_unlock(&handler_mutex);
}

void update_tracking_consent(jint consent) {
    tracking_consent = (uint8_t) consent;
}

void update_app_start_time_millis(jlong time_ms) {
    global_app_start_time_millis = time_ms;
}

/// Jni bindings
extern "C" JNIEXPORT void JNICALL
Java_com_datadog_android_ndk_internal_NdkCrashReportsFeature_registerSignalHandler(
        JNIEnv *env,
        jobject /* this */,
        jstring storage_path,
        jint consent,
        jlong app_start_time_millis) {

    update_main_context(env, storage_path);
    update_tracking_consent(consent);
    update_app_start_time_millis(app_start_time_millis);
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
