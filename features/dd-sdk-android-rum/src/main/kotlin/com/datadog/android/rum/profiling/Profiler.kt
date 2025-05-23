/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.profiling

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ProfilingManager
import androidx.core.os.StackSamplingRequestBuilder
import android.util.Log
import androidx.core.os.requestProfiling
import java.util.concurrent.Executors

object Profiler {

    lateinit var filePath: String

    private val profilingExecutor = Executors.newSingleThreadExecutor()
    private var profilingManager: ProfilingManager? = null
    private var cancellationSignal: CancellationSignal = CancellationSignal()

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

    @SuppressLint("WrongConstant")
    fun startProfilingManagerProfiling(context: Context, samplingIntervalMs: Long) {
        val samplesPerSecond = 1000 / samplingIntervalMs
        if (profilingManager == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                requestProfiling(
                    context,
                    StackSamplingRequestBuilder()
                        .setCancellationSignal(cancellationSignal)
                        .setSamplingFrequencyHz(samplesPerSecond.toInt())
                        .setTag("AppStartup")
                        .build(),
                    profilingExecutor
                ) { result ->
                    Log.v(
                        "ProfilingManager",
                        "Profiling result filepath: ${result.resultFilePath} error message: " +
                                "${result.errorMessage}  and error code ${result.errorCode}"
                    )
                }
            }
        }
    }

    fun stopProfilingManagerProfiling() {
        cancellationSignal?.cancel()
    }

    private external fun stopTracing(filePath: String)

    private external fun startTracing(samplesPerSecond: Long, filePath: String)
}
