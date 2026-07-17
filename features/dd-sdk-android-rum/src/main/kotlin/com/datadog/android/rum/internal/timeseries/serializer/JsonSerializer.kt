/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.serializer

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.rum.internal.timeseries.DataPoint
import com.google.gson.JsonObject

internal fun interface JsonSerializer<T : Any> {
    fun serialize(datadogContext: DatadogContext, dataPoints: List<DataPoint<T>>): JsonObject?
}

internal object TimeseriesAttributes {
    const val KEY_TIMESERIES = "timeseries"
    const val KEY_SCHEMA = "schema"
    const val KEY_NAME = "name"
}
