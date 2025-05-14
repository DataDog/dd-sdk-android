////
//// Created by Marius Constantin on 02/05/2025.
////
//
#include <jni.h>
#include <android/log.h>
#include <memory>
#include <ctime>
#include "simpleperf.h"          // from include/simpleperf/app_api/cpp

#define  LOGI(...) __android_log_print(ANDROID_LOG_INFO,"SimpleperfBridge",__VA_ARGS__)
using simpleperf::ProfileSession;
using simpleperf::RecordOptions;

namespace {
    std::unique_ptr<ProfileSession> g_session;
    const char* kPackageName = "com.example.profiler";
}

extern "C"
JNIEXPORT void JNICALL
Java_com_datadog_android_rum_profiling_Profiler_startTracing(JNIEnv *env, jobject /* this */, jlong samplesPerSecond,
                                                             jstring jOutputPath) {
    if (g_session) { LOGI("session already running"); return; }

    const char* filename = env->GetStringUTFChars(jOutputPath, nullptr);
    RecordOptions opts;
    opts.SetSampleFrequency(samplesPerSecond);          // e.g. 200 Hz
    opts.SetOutputFilename(filename);
    opts.SetEvent("cpu-clock:u");                       // e.g. cpu-cycles
    opts.RecordDwarfCallGraph();
    auto thread_ids = std::vector<pid_t>();
    thread_ids.push_back(gettid());
    opts.SetSampleThreads(thread_ids); // record only this thread
    g_session = std::make_unique<ProfileSession>();
    // let's print the command line that will be executed
    std::vector<std::string> record_args = opts.ToRecordArgs();
    std::string cmd = "simpleperf record";
    for (const auto& arg : record_args) {
        cmd += " " + arg;
    }
    LOGI("Command line: %s", cmd.c_str());
    try {
        g_session->StartRecording(opts);
    }
    catch (const std::exception& e) {
        LOGI("Failed to start tracing: %s", e.what());
    }
    LOGI("Started tracing with sampling interval %ld", samplesPerSecond);
    env->ReleaseStringUTFChars(jOutputPath, filename);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_datadog_android_rum_profiling_Profiler_stopTracing(JNIEnv *env,
                                                            jobject /* this */,
                                                            jstring jOutputPath) {
    if (!g_session) return;

    g_session->StopRecording();
    g_session.reset();
    LOGI("Stopped tracing");
}
