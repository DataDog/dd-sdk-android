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
#include "utils/backtrace-handler.h"
#include "utils/datetime-utils.h"
#include "utils/file-utils.h"
#include "utils/signal-monitor.h"
#include "utils/string-utils.h"

typedef std::string string;

static struct RumContext {
    string application_id;
    string session_id;
    string view_id;

    RumContext() : application_id(), session_id(), view_id() {}

} rum_context;

static struct Context {
    string generic_message;
    string emergency_status;
    string error_kind;
    string logger_name;
    string environment;
    string service_name;
    string storage_dir;

    Context() :
            environment(),
            service_name(),
            storage_dir(),
            generic_message("Native crash detected"),
            emergency_status("emergency"),
            error_kind("Native"),
            logger_name("crash") {
    }
} main_context;


static const char *LOG_TAG = "DatadogNdkCrashReporter";
static pthread_mutex_t handler_mutex = PTHREAD_MUTEX_INITIALIZER;
static const uint8_t tracking_consent_pending = 0;
static const uint8_t tracking_consent_granted = 1;
static uint8_t tracking_consent = tracking_consent_pending; // 0 - PENDING, 1 - GRANTED, 2 - NOT-GRANTED


std::string get_serialized_log(int signal,
                               const char *signal_name,
                               const char *error_message,
                               const char *date,
                               const std::string backtrace) {
    std::string serializedLog = "{ ";
    if (!rum_context.application_id.empty()) {
        serializedLog.append(R"("application_id": ")").append(rum_context.application_id)
                .append("\",");
    }
    if (!rum_context.session_id.empty()) {
        serializedLog.append(R"("session_id": ")").append(rum_context.session_id)
                .append("\",");
    }
    if (!rum_context.view_id.empty()) {
        serializedLog.append(R"("view.id": ")").append(rum_context.view_id)
                .append("\",");
    }
    // these values are either constants or they are marked as NonNull in JVM so we do not have
    // to check them here.
    char tags[105]; // max 105 characters for the environment name
    snprintf(tags, sizeof(tags), "env:%s", main_context.environment.c_str());
    serializedLog.append(R"("message": ")").append(main_context.generic_message).append("\",");
    serializedLog.append(R"("service": ")").append(main_context.service_name).append("\",");
    serializedLog.append(R"("logger.name": ")").append(main_context.logger_name).append("\",");
    serializedLog.append(R"("ddtags": ")").append(tags).append("\",");
    serializedLog.append(R"("status": ")").append(main_context.emergency_status).append("\",");
    serializedLog.append(R"("date": ")").append(date).append("\",");
    serializedLog.append(R"("error.stack": ")").append(backtrace).append("\",");
    serializedLog.append(R"("error.message": ")").append(error_message).append("\",");
    char formatted_signal_message[30];
    const size_t messageSize =
            sizeof(formatted_signal_message) / sizeof(formatted_signal_message[0]);
    snprintf(formatted_signal_message,
             messageSize, "%s: %d",
             signal_name,
             signal);
    serializedLog.append(R"("error.signal": ")").append(formatted_signal_message).append("\",");
    serializedLog.append(R"("error.kind": ")").append(main_context.error_kind).append("\"");
    serializedLog.append(" }");
    return serializedLog;
}

void crash_signal_intercepted(int signal, const char *signal_name, const char *error_message) {
    // sync everything
    pthread_mutex_lock(&handler_mutex);
    if (tracking_consent != tracking_consent_granted) {
        pthread_mutex_unlock(&handler_mutex);
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

    // format the current GMT time
    char date[100];
    const char *format = "%Y-%m-%d'T'%H:%M:%S.000Z";
    format_date(date, sizeof(date), format);

    // extract the generate_backtrace
    std::string backtrace = backtrace::generate_backtrace();

    // serialize the log
    std::string serialized_log = get_serialized_log(signal, signal_name, error_message, date,
                                                    backtrace);

    // dump the log into a new file
    char filename[200];
    // The ARM_32 processors will use an unsigned long long to represent the uint_64. We will pick the
    // String format that fits both ARM_32 and ARM_64 (llu).
    #pragma clang diagnostic push
    #pragma clang diagnostic ignored "-Wformat"
    snprintf(filename, sizeof(filename), "%s/%llu", main_context.storage_dir.c_str(),
             time_since_epoch());
    #pragma clang diagnostic pop
    std::ofstream logs_file_output_stream(filename, std::ofstream::out | std::ofstream::app);
    const char *text = serialized_log.c_str();
    if (logs_file_output_stream.is_open()) {
        logs_file_output_stream << text << "\n";
    }
    logs_file_output_stream.close();

    pthread_mutex_unlock(&handler_mutex);
}

void update_main_context(JNIEnv *env,
                         jstring storage_path,
                         jstring service_name,
                         jstring environment) {
    using namespace stringutils;
    pthread_mutex_lock(&handler_mutex);
    main_context.storage_dir = copy_to_string(env, storage_path);
    main_context.service_name = copy_to_string(env, service_name);
    main_context.environment = copy_to_string(env, environment);
    pthread_mutex_unlock(&handler_mutex);
}

void update_rum_context(JNIEnv *env,
                        jstring application_id,
                        jstring session_id,
                        jstring view_id) {
    using namespace stringutils;
    pthread_mutex_lock(&handler_mutex);
    rum_context.application_id = copy_to_string(env, application_id);
    rum_context.session_id = copy_to_string(env, session_id);
    rum_context.view_id = copy_to_string(env, view_id);
    pthread_mutex_unlock(&handler_mutex);
}

void update_tracking_consent(jint consent) {
    tracking_consent = (uint8_t) consent;
}


/// Jni bindings
extern "C" JNIEXPORT void JNICALL
Java_com_datadog_android_ndk_NdkCrashReportsPlugin_registerSignalHandler(
        JNIEnv *env,
        jobject handler,
        jstring storage_path,
        jstring service_name,
        jstring environment,
        jint consent) {

    update_main_context(env, storage_path, service_name, environment);
    update_tracking_consent(consent);
    install_signal_handlers();
}


extern "C" JNIEXPORT void JNICALL
Java_com_datadog_android_ndk_NdkCrashReportsPlugin_unregisterSignalHandler(
        JNIEnv *env,
        jobject /* this */) {
    uninstall_signal_handlers();
}

extern "C" JNIEXPORT void JNICALL
Java_com_datadog_android_ndk_NdkCrashReportsPlugin_updateTrackingConsent(
        JNIEnv *env,
        jobject /* this */,
        jint consent) {
    update_tracking_consent(consent);
}

extern "C" JNIEXPORT void JNICALL
Java_com_datadog_android_ndk_NdkCrashReportsPlugin_updateRumContext(
        JNIEnv *env,
        jobject /* this */,
        jstring application_id,
        jstring session_id,
        jstring view_id) {
    update_rum_context(env, application_id, session_id, view_id);
}