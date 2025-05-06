/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.profiling

import android.content.Context

object Profiler {

    private val usePerfetto = true
    lateinit var filePath: String

    init {
        System.loadLibrary("datadog-profiling")
    }

    fun startProfiling(context: Context, samplingIntervalMs: Long) {
        if (usePerfetto) {
            filePath = "${context.getExternalFilesDir(null)}/${System.currentTimeMillis()}.pftrace"
            // Start Perfetto profiling logic
            startTracing(samplingIntervalMs, filePath)
        } else {
            // Start alternative profiling logic
            MergeTraceDumper.getInstance(context).startDumpingTrace(samplingIntervalMs)
        }
    }

    fun stopProfiling() {
        if(usePerfetto) {

            // Stop Perfetto profiling logic
            stopTracing(filePath)
        } else {
            // Stop alternative profiling logic
            MergeTraceDumper.getInstance().stopDumpingTrace()
        }
    }

    private external fun stopTracing(filePath: String)

    private external fun startTracing(samplingIntervalMs: Long, filePath: String)
}