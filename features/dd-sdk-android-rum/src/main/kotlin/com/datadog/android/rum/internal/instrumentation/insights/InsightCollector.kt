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
    fun onSlowFrame(startedTimestamp: Long, durationNs: Long)
    fun onAction()
    fun onLongTask(startedTimestamp: Long, durationNs: Long)
    fun onNetworkRequest(startedTimestamp: Long, durationNs: Long)
    fun addUpdateListener(listener: InsightsUpdatesListener)
    fun removeUpdateListener(listener: InsightsUpdatesListener)
}

interface InsightsUpdatesListener {
    fun onUpdate()
}