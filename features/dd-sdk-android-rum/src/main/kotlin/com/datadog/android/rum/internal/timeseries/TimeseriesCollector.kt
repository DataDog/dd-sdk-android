/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries

import com.datadog.android.internal.lifecycle.ProcessLifecycleMonitor
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface TimeseriesCollector : ProcessLifecycleMonitor.Callback {
    fun onSessionStart(sessionType: RumSessionType)
    fun onSessionStop()
    fun onRumContextUpdate(newRumContext: RumContext)

    override fun onStarted() = Unit

    override fun onStopped() = Unit
}
