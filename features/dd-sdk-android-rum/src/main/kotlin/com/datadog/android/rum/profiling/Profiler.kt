/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.profiling

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.CancellationSignal
import androidx.core.os.StackSamplingRequestBuilder
import android.util.Log
import androidx.core.os.ProfilingRequest
import androidx.core.os.SystemTraceRequestBuilder
import androidx.core.os.requestProfiling
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors

object Profiler {

    private lateinit var filePath: String

    private val profilingExecutor = Executors.newSingleThreadExecutor()
    private var cancellationSignals = mutableMapOf<String, Pair<Long, CancellationSignal>>()

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
    fun startProfilingManagerProfiling(context: Context, samplingIntervalMs: Long, tag: String = "AppStartup") {
        val samplesPerSecond = 1000 / samplingIntervalMs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val startTimeInNanos = System.currentTimeMillis()
            cancellationSignals.remove(tag)?.second?.cancel()
            val cancellationSignal = CancellationSignal()
            val request = randomRequest(samplesPerSecond, tag, cancellationSignal)
            cancellationSignals[tag] = Pair(startTimeInNanos, cancellationSignal)
            requestProfiling(
                context,
                request,
                profilingExecutor
            ) { result ->
                val startTimestamp = cancellationSignals.remove(tag)?.first ?: 0L
                Log.v(
                    "ProfilingManager",
                    "Profiling result filepath: ${result.resultFilePath} error message: " +
                            "${result.errorMessage}  and error code ${result.errorCode}"
                )
                result.resultFilePath?.let { filePath ->
                    val endTimestamp = System.currentTimeMillis()
                    val file = File(filePath)
                    val newFilePath = "profiling_${startTimestamp}_${endTimestamp}.trace"
                    val datadogProfilingDirectory = File(context.getExternalFilesDir(null), "datadog_profiling")
                    if (!datadogProfilingDirectory.exists()) {
                        datadogProfilingDirectory.mkdirs()
                    }
                   val newFile = File(datadogProfilingDirectory, newFilePath)
                   Files.move(file.toPath(), newFile.toPath())
                }
            }
        }
    }

    private fun randomRequest(
        samplesPerSecond: Long,
        tag: String,
        cancellationSignal: CancellationSignal
    ): ProfilingRequest {
//        val random = (0..1).random()
//        return if (random == 0) {
//            systemTraceRequest(tag, cancellationSignal)
//        } else {
//            stackSamplingRequest(samplesPerSecond, tag, cancellationSignal)
//        }
        return stackSamplingRequest(samplesPerSecond, tag, cancellationSignal)
    }

    @SuppressLint("NewApi")
    private fun systemTraceRequest(tag: String, cancellationSignal: CancellationSignal): ProfilingRequest {
        return SystemTraceRequestBuilder()
            .setTag(tag)
            .setCancellationSignal(cancellationSignal)
            .build()
    }

    @SuppressLint("NewApi")
    private fun stackSamplingRequest(
        samplesPerSecond: Long,
        tag: String,
        cancellationSignal: CancellationSignal
    ): ProfilingRequest {
        return StackSamplingRequestBuilder()
            .setSamplingFrequencyHz(samplesPerSecond.toInt())
            .setCancellationSignal(cancellationSignal)
            .setTag(tag)
            .build()
    }


    fun stopProfilingManagerProfiling(tag: String = "AppStartup") {
        val pair = cancellationSignals[tag]
        pair?.second?.cancel()
    }

    private external fun stopTracing(filePath: String)

    private external fun startTracing(samplesPerSecond: Long, filePath: String)
}
