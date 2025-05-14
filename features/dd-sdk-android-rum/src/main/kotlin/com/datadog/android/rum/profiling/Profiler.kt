/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.profiling

import android.content.Context

object Profiler {

    lateinit var filePath: String

    init {
        System.loadLibrary("datadog-profiling")
    }

    fun startProfiling(context: Context, samplingIntervalMs: Long) {
        // Start alternative profiling logic
        MergeTraceDumper.getInstance(context).startDumpingTrace(samplingIntervalMs)
    }

    fun startSimpleperfProfiling(context: Context, samplingIntervalMs: Long) {
        filePath = "${context.getExternalFilesDir(null)}/${System.currentTimeMillis()}.data"
        val samplesPerSecond = 1000 / samplingIntervalMs
        startTracing(samplesPerSecond, filePath)
    }

    fun stopProfiling() {
        MergeTraceDumper.getInstance().stopDumpingTrace()
    }

    fun stopSimpleperfProfiling() {
        stopTracing(filePath)
    }

    private external fun stopTracing(filePath: String)

    private external fun startTracing(samplesPerSecond: Long, filePath: String)
}
