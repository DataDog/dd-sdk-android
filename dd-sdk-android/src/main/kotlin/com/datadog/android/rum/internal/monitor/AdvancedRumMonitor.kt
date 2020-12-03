/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.monitor

import com.datadog.android.core.internal.domain.Time
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.domain.model.ViewEvent
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface AdvancedRumMonitor : RumMonitor {

    fun resetSession()

    fun viewTreeChanged(eventTime: Time)

    fun waitForResourceTiming(key: String)

    fun updateViewLoadingTime(key: Any, loadingTimeInNs: Long, type: ViewEvent.LoadingType)

    fun addResourceTiming(key: String, timing: ResourceTiming)

    fun addCrash(
        message: String,
        source: RumErrorSource,
        throwable: Throwable
    )
}
