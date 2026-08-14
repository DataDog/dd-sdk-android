/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries

import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.domain.scope.RumViewType
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface TimeseriesCollector {
    fun onSessionStart()
    fun onSessionStop()
    fun onViewTypeUpdate(newViewType: RumViewType)

    @NoOpImplementation
    interface Factory {
        fun create(
            applicationId: String,
            sessionId: String,
            sessionType: RumSessionType,
            viewType: RumViewType?
        ): TimeseriesCollector
    }
}
