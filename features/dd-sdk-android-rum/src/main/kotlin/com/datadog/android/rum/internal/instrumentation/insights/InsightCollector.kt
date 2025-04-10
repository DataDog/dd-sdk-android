/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.instrumentation.insights

import com.datadog.android.lint.InternalApi
import com.datadog.tools.annotation.NoOpImplementation

@InternalApi
@NoOpImplementation
interface InsightsCollector {
    var maxSize: Int
    var updateIntervalMs: Long
    fun addUpdateListener(listener: InsightsUpdatesListener)
    fun removeUpdateListener(listener: InsightsUpdatesListener)

    fun onNewView(viewId: String, name: String)
    fun onAction()
    fun onLongTask(startedTimestamp: Long, durationNs: Long)
    fun onSlowFrame(startedTimestamp: Long, durationNs: Long)
    fun onNetworkRequest(startedTimestamp: Long, durationNs: Long)
    fun onCpuVital(cpuTicks: Double?)
    fun onMemoryVital(memoryValue: Double?)
    fun onSlowFrameRate(rate: Double?)
}

interface InsightsUpdatesListener {
    fun onDataUpdated()
}
