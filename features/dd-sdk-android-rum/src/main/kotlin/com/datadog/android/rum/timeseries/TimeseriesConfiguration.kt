/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.timeseries

import com.datadog.android.rum.ExperimentalRumApi

/**
 * Configuration for memory and CPU timeseries collection.
 *
 * Use [Builder] to create an instance.
 */
class TimeseriesConfiguration internal constructor(
    internal val enabledTypes: Set<TimeseriesType>
) {

    /**
     * A Builder for [TimeseriesConfiguration].
     */
    @ExperimentalRumApi
    class Builder {

        private var enabledTypes: Set<TimeseriesType> = TimeseriesType.entries.toSet()

        /**
         * Restricts collection to the provided timeseries types.
         *
         * By default, all supported timeseries types are collected.
         * Passing an empty array disables collection of every timeseries type.
         *
         * @param types the timeseries types to collect.
         */
        fun collectOnly(vararg types: TimeseriesType): Builder = apply {
            enabledTypes = types.toSet()
        }

        /** Builds a [TimeseriesConfiguration] from the current builder state. */
        fun build(): TimeseriesConfiguration = TimeseriesConfiguration(
            enabledTypes = enabledTypes
        )
    }

    companion object {

        /** Default [TimeseriesConfiguration] built with all default settings. */
        @ExperimentalRumApi
        val DEFAULT: TimeseriesConfiguration = Builder().build()

        /** Default number of samples to accumulate before emitting a timeseries event. */
        internal const val DEFAULT_BUFFER_SIZE: Int = 120

        /** Default sampling interval in milliseconds. */
        internal const val DEFAULT_INTERVAL_MS: Long = 1000L
    }
}
