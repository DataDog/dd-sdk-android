////
//// Created by Marius Constantin on 02/05/2025.
////
//
#include <jni.h>
#include <perfetto.h>
#include <fstream>
#include <iostream>
#include <thread>
#include <memory>
#include <perfetto.h>
#include "android/log.h"

using namespace perfetto;

// Keep the session around so stopTracing() can see it.
static std::unique_ptr<TracingSession> g_session;

extern "C"
JNIEXPORT void JNICALL
Java_com_datadog_android_rum_profiling_Profiler_startTracing(JNIEnv *env, jobject /* this */, jlong samplingInterval,
                                                             jstring jOutputPath) {
    // 1. Initialize once
    static bool initialized = false;
    if (!initialized) {
        TracingInitArgs args;
        args.backends = perfetto::k;
        Tracing::Initialize(args);
        initialized = true;
    }
    const char *path = env->GetStringUTFChars(jOutputPath, nullptr);


    // 2) Build a TraceConfig with linux.perf data source
    perfetto::TraceConfig cfg;

    // 3) Fill PerfEventConfig
    perfetto::protos::gen::PerfEventConfig pe_cfg;
    // 500 Hz sampling = one sample every 2 ms
    pe_cfg.set_sampling_frequency(500);
    pe_cfg.mutable_timebase()->set_frequency(500);  // :contentReference[oaicite:0]{index=0}
    pe_cfg.set_all_cpus(true);
    // Enable callstack capture in user & kernel
    auto* cs = pe_cfg.mutable_callstack_sampling();
    cs->set_kernel_frames(true);
    cs->set_user_frames(
            perfetto::protos::gen::PerfEventConfig_UnwindMode::PerfEventConfig_UnwindMode_UNWIND_FRAME_POINTER);

    // 4) Attach it to the data source
    auto* ds = cfg.add_data_sources()->mutable_config();
    ds->set_name("linux.perf");
    ds->set_perf_event_config_raw(pe_cfg.SerializeAsString());

//    // 2. Build config with sampling
//    TraceConfig cfg;
//    perfetto::protos::gen::PerfEventConfig pe_cfg;
//    pe_cfg.set_sampling_frequency(static_cast<uint32_t>(1000));
//    pe_cfg.mutable_timebase()->set_frequency(1000);
//    pe_cfg.set_all_cpus(true);
//
//    // Configure CPU profiling
//    pe_cfg.set_sampling_frequency(1000);
//    pe_cfg.set_ring_buffer_pages(256);  // Increase ring buffer size
//    pe_cfg.set_remote_descriptor_timeout_ms(1000);

//    auto *cs = pe_cfg.mutable_callstack_sampling();
//    cs->set_kernel_frames(true);
//    cs->set_user_frames(
//            perfetto::protos::gen::PerfEventConfig_UnwindMode::PerfEventConfig_UnwindMode_UNWIND_FRAME_POINTER);

    // Add CPU counter data source
//    auto *cpu_counter = cfg.add_data_sources()->mutable_config();
//    cpu_counter->set_name("linux.perf.cpu");
//    cpu_counter->set_perf_event_config_raw(pe_cfg.SerializeAsString());


    cfg.set_duration_ms(0);  // no automatic stop
    cfg.set_write_into_file(true);
    cfg.set_file_write_period_ms(2);
    cfg.set_output_path(path);
    cfg.add_buffers()->set_size_kb(4096);

    // 3. Start the session
    g_session = Tracing::NewTrace();
    g_session->Setup(cfg);
    g_session->StartBlocking();
    env->ReleaseStringUTFChars(jOutputPath, path);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_datadog_android_rum_profiling_Profiler_stopTracing(JNIEnv *env,
                                                            jobject /* this */,
                                                            jstring jOutputPath) {
    const char *path = env->GetStringUTFChars(jOutputPath, nullptr);
    if (g_session) {
        // 4. Stop and dump
        g_session->StopBlocking();
//        std::vector<char> trace_data(g_session->ReadTraceBlocking());
//        std::ofstream output;
//        output.open(path, std::ios::out | std::ios::binary);
//        output.write(trace_data.data(),
//                     static_cast<std::streamsize>(trace_data.size()));
//        output.close();
        __android_log_print(ANDROID_LOG_INFO, "Perfetto", "Trace written in %s file.", path);
    } else {
        __android_log_print(ANDROID_LOG_ERROR, "Perfetto", "No active tracing session.");
    }
    env->ReleaseStringUTFChars(jOutputPath, path);
}
