/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import com.datadog.android.core.internal.utils.devLogger
import java.io.File
import java.util.Locale

internal class SessionReplayVitals(val storageFolder: File) {
    private val workerThread = WorkerThread()
    private var times = 0
    private var currentTotal: Double = 0.0

    init {
        workerThread.start()
    }

    val cpuProfilingProvider by lazy {
        CpuProfilingProvider()
    }

    fun logVitals() {
        workerThread.post {
            cpuProfilingProvider.readCpuData().let { currentValue ->
                times++
                currentTotal +=currentValue
                val currentMean = currentTotal/times
                devLogger.v(
                    CPU_CONSUMPTION_MESSAGE_FORMAT.format(
                        Locale.US,
                        currentValue,
                        currentMean
                    )
                )
            }
            val storageSpaceInKb = storageFolder.size() / 1024
            devLogger.v(STORAGE_SPACE_MESSAGE_FORMAT.format(Locale.US, storageSpaceInKb))
        }
    }

    private fun File.size(): Long {
        var length = 0L
        listFiles()?.filter {it.isFile }?.forEach {
            length += it.length()
        }
        return length
    }

    companion object {
        const val VITALS_TAG = "SESSION_REPLAY"
        const val VITALS_CPU_TAG = "$VITALS_TAG CPU TICKS/SECOND "
        const val VITALS_STORAGE_SPACE_TAG = "$VITALS_TAG STORAGE"
        const val CPU_CONSUMPTION_MESSAGE_FORMAT =
            "$VITALS_CPU_TAG currentValue: %.2f and mean: %.2f "
        const val STORAGE_SPACE_MESSAGE_FORMAT = "$VITALS_STORAGE_SPACE_TAG %d KB"
    }
}