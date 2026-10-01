/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.timeseries

/**
 * Configuration for memory and CPU timeseries collection.
 *
 * @param collectTypes the timeseries types to collect. Passing an empty set disables
 * collection of every timeseries type.
 */
class TimeseriesConfiguration(collectTypes: Set<TimeseriesType>) {

    internal val enabledTypes: Set<TimeseriesType> = collectTypes.toSet()

    // These two settings let tests shorten sampling and buffer fill waits for tests.
    // Keep them internal and hide their accessors from Java callers until they are part of the public API.
    @get:JvmSynthetic
    @set:JvmSynthetic
    internal var bufferSize: Int = DEFAULT_BUFFER_SIZE

    @get:JvmSynthetic
    @set:JvmSynthetic
    internal var intervalMs: Long = DEFAULT_INTERVAL_MS

    companion object {

        /** Default [TimeseriesConfiguration] collecting CPU and MEMORY timeseries types. */
        val DEFAULT: TimeseriesConfiguration = TimeseriesConfiguration(setOf(TimeseriesType.CPU, TimeseriesType.MEMORY))

        /** Default number of samples to accumulate before emitting a timeseries event. */
        internal const val DEFAULT_BUFFER_SIZE: Int = 120

        /** Default sampling interval in milliseconds. */
        internal const val DEFAULT_INTERVAL_MS: Long = 1000L
    }
}
