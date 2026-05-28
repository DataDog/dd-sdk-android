/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.serializer

import com.datadog.android.rum.internal.timeseries.DataPoint
import com.google.gson.JsonObject

internal fun interface JsonSerializer<T : Any> {
    fun serialize(dataPoints: List<DataPoint<T>>): JsonObject?
}
