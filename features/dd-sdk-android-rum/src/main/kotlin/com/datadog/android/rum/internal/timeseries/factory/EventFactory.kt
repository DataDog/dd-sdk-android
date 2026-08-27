/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.factory

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.timeseries.DataPoint

internal interface EventFactory<T : Any, E : Any> {
    val eventName: String

    fun create(
        datadogContext: DatadogContext,
        rumContext: RumContext,
        dataPoints: List<DataPoint<T>>
    ): E?
}
