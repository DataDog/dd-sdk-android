//
// Datadog Native Library.
//
#include <jni.h>
#include <sstream>
#include <cstring>
#include <fstream>
#include "utils/signal-monitor.h"
#include "utils/backtrace-handler.h"
#include "datadog-native-lib.h"
#include "utils/datetime.h"
#include "utils/fileutils.h"

static const char *STORAGE_DIR;
static const char *SERVICE_NAME;
static const char *LOGGER_NAME;
static const char *GENERIC_MESSAGE;
static const char *EMERGENCY_STATUS;
static const char *ENVIRONMENT;
static const char *ERROR_KIND = "Native";

void onCrashSignalIntercepted(int signal, const char* signalName, const char* errorMessage) {
    // sync everything
    static pthread_mutex_t handlerMutex = PTHREAD_MUTEX_INITIALIZER;
    pthread_mutex_lock(&handlerMutex);

    // create crash reporting directory if it does not exist
    createDirIfNotExists(STORAGE_DIR);

    // format the current GMT time
    size_t  bufferSize = 80;
    char buffer[bufferSize];
    const char *format = "%Y-%m-%d'T'%H:%M:%S.000Z";
    dateTimeWithFormat(buffer, bufferSize, format);

    // extract the backtrace
    std::ostringstream oss;
    dumpBacktrace(oss);
    const char *backtrace = oss.str().data();
    oss.clear();
    std::ostringstream dumpstream;

    // serialize the log
    dumpstream << "{ ";
    dumpstream << "\"message\": \"" << GENERIC_MESSAGE << "\",";
    dumpstream << "\"service\": \"" << SERVICE_NAME << "\",";
    dumpstream << "\"logger.name\": \"" << LOGGER_NAME << "\",";
    dumpstream << "\"env\": \"" << ENVIRONMENT << "\",";
    dumpstream << "\"status\": \"" << EMERGENCY_STATUS << "\",";
    dumpstream << "\"date\": \"" << buffer << "\",";
    dumpstream << "\"error.stack\": \"" << backtrace << "\",";
    dumpstream << "\"error.message\": \"" << errorMessage <<  " : " << signalName << "\",";
    dumpstream << "\"error.signal\": \"" << signal << "\",";
    dumpstream << "\"error.kind\": \"" << ERROR_KIND << "\"";
    dumpstream << " }";

    // dump the log into a new file
    std::ostringstream filename;
    filename << STORAGE_DIR << "/" << timeSinceEpochMillisec();
    std::ofstream logs_file(filename.str(), std::ofstream::out | std::ofstream::app);
    if (logs_file.is_open()) {
        logs_file << dumpstream.str() << "\n";
    }
    logs_file.close();

    pthread_mutex_unlock(&handlerMutex);
}


/// Jni bindings
extern "C" JNIEXPORT void JNICALL
Java_com_datadog_android_error_internal_DatadogNdkCrashHandler_registerSignalHandler(
        JNIEnv *env,
        jobject handler,
        jstring storage_path,
        jstring service_name,
        jstring logger_name,
        jstring generic_message,
        jstring emergency_status,
        jstring environment) {
    STORAGE_DIR = env->GetStringUTFChars(storage_path, 0);
    SERVICE_NAME = env->GetStringUTFChars(service_name, 0);
    LOGGER_NAME = env->GetStringUTFChars(logger_name, 0);
    GENERIC_MESSAGE = env->GetStringUTFChars(generic_message, 0);
    EMERGENCY_STATUS = env->GetStringUTFChars(emergency_status, 0);
    ENVIRONMENT = env->GetStringUTFChars(environment, 0);
    installSignalHandlers(env);
}

extern "C" JNIEXPORT void JNICALL
Java_com_datadog_android_error_internal_DatadogNdkCrashHandler_unregisterSignalHandler(
        JNIEnv *env,
        jobject /* this */) {
    uninstallSignalHandlers();
}
