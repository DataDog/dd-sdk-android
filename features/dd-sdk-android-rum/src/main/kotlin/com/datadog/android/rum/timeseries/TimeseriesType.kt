/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.timeseries

/**
 * Type of device timeseries that can be collected by RUM.
 */
enum class TimeseriesType {

    /** CPU usage timeseries. */
    CPU,

    /** Memory usage timeseries. */
    MEMORY
}
