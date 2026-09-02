/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.provider

import androidx.annotation.WorkerThread
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.internal.timeseries.DataPoint
import java.util.concurrent.TimeUnit

internal abstract class DataPointsReader<T : Any>(private val timeProvider: TimeProvider) {

    abstract val intervalMs: Long

    @WorkerThread
    protected abstract fun readValue(): T?

    @WorkerThread
    open fun read(): DataPoint<T>? = readValue()?.let { value ->
        DataPoint(
            value = value,
            timestampNs = TimeUnit.MILLISECONDS.toNanos(timeProvider.getServerTimestampMillis())
        )
    }
}
