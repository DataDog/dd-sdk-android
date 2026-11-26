/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries

/**
 * A sample of a timeseries.
 *
 * @param T the type of the sampled value.
 * @param deviceTimestampNs the device epoch time of the sample, in nanoseconds. The server time
 * offset is applied when the event is built.
 * @param value the sampled value.
 */
internal data class DataPoint<out T : Any>(val deviceTimestampNs: Long, val value: T)
