/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

#include "datadog-native-lib.h"

#include <cstring>
#include <fstream>
#include <jni.h>
#include <pthread.h>
#include <sstream>

#include "android/log.h"
#include "utils/backtrace-handler.h"
#include "utils/datetime.h"
#include "utils/fileutils.h"
#include "utils/signal-monitor.h"

static const char *GENERIC_MESSAGE = "Native crash detected";
static const char *EMERGENCY_STATUS = "emergency";
static const char *ENVIRONMENT = "";
static const char *ERROR_KIND = "Native";
static const char *LOGGER_NAME = "crash";
static const char *LOG_TAG = "DatadogNdkCrashReporter";
static const char *SERVICE_NAME = "";
const char *STORAGE_DIR = nullptr;

std::string get_serialized_log(int signal,
                               const char *signal_name,
                               const char *error_message,
                               const char *date,
                               const char *backtrace) {
    std::string serializedLog;
    serializedLog.append("{ ");
    serializedLog.append(R"("message": ")").append(GENERIC_MESSAGE).append("\",");
    serializedLog.append(R"("service": ")").append(SERVICE_NAME).append("\",");
    serializedLog.append(R"("logger.name": ")").append(LOGGER_NAME).append("\",");
    serializedLog.append(R"("env": ")").append(ENVIRONMENT).append("\",");
    serializedLog.append(R"("status": ")").append(EMERGENCY_STATUS).append("\",");
    serializedLog.append(R"("date": ")").append(date).append("\",");
    serializedLog.append(R"("error.stack": ")").append(backtrace).append("\",");
    serializedLog.append(R"("error.message": ")").append(error_message).append("\",");
    char formattedSignalMessage[30];
    const size_t messageSize = sizeof(formattedSignalMessage) / sizeof(formattedSignalMessage[0]);
    snprintf(formattedSignalMessage,
             messageSize, "%s: %d",
             signal_name,
             signal);
    serializedLog.append(R"("error.signal": ")").append(formattedSignalMessage).append("\",");
    serializedLog.append(R"("error.kind": ")").append(ERROR_KIND).append("\"");
    serializedLog.append(" }");
    return serializedLog;
}

void crash_signal_intercepted(int signal, const char *signal_name, const char *error_message) {
    // sync everything
    static pthread_mutex_t handlerMutex = PTHREAD_MUTEX_INITIALIZER;
    pthread_mutex_lock(&handlerMutex);

    if (STORAGE_DIR == nullptr) {
        __android_log_write(ANDROID_LOG_ERROR, LOG_TAG,
                            "The crash reports storage directory file path was null");
        pthread_mutex_unlock(&handlerMutex);
        return;

    }

    // create crash reporting directory if it does not exist
    if (!fileutils::create_dir_if_not_exists(STORAGE_DIR)) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                            "Was unable to create the NDK reports storage directory: %s",
                            STORAGE_DIR);
        pthread_mutex_unlock(&handlerMutex);
        return;
    }

    // format the current GMT time
    char date[80];
    const char *format = "%Y-%m-%d'T'%H:%M:%S.000Z";
    format_date(date, sizeof(date), format);

    // extract the generate_backtrace
    const char *backtrace = backtrace::generate_backtrace();

    // serialize the log
    std::string serialized_log = get_serialized_log(signal, signal_name, error_message, date,
                                                    backtrace);

    // dump the log into a new file
    char filename[200];
    snprintf(filename, 200, "%s/%llu", STORAGE_DIR, time_since_epoch());
    std::ofstream logs_file_output_stream(filename, std::ofstream::out | std::ofstream::app);
    if (logs_file_output_stream.is_open()) {
        logs_file_output_stream << serialized_log << "\n";
    }
    logs_file_output_stream.close();

    pthread_mutex_unlock(&handlerMutex);
}

/// Jni bindings
extern "C" JNIEXPORT void JNICALL
Java_com_datadog_android_ndk_NdkCrashReportsPlugin_registerSignalHandler(
        JNIEnv *env,
        jobject handler,
        jstring storage_path,
        jstring service_name,
        jstring environment) {
    STORAGE_DIR = env->GetStringUTFChars(storage_path, 0);
    SERVICE_NAME = env->GetStringUTFChars(service_name, 0);
    ENVIRONMENT = env->GetStringUTFChars(environment, 0);
    install_signal_handlers();
}

extern "C" JNIEXPORT void JNICALL
Java_com_datadog_android_ndk_NdkCrashReportsPlugin_unregisterSignalHandler(
        JNIEnv *env,
        jobject /* this */) {
    uninstall_signal_handlers();
}
