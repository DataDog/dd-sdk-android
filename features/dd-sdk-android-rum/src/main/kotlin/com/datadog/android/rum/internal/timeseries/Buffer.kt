/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.timeseries

internal class Buffer<T : Any>(private val size: Int) {
    private val items = ArrayList<DataPoint<T>>(size)

    fun add(dataPoint: DataPoint<T>) = items.add(dataPoint)

    fun isFull(): Boolean = items.size >= size

    fun drain() = items.toList().also { items.clear() }
}
