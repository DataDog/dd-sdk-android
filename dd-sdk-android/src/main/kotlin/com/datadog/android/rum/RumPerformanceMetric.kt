/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

/**
 * A cross-platform technical performance metric.
 * @see [RumMonitor]
 */
enum class RumPerformanceMetric {
    /** The amount of time Flutter spent in its `build` method for this view. */
    FLUTTER_BUILD_TIME,

    /** The amount of time Flutter spent rasterizing the view. */
    FLUTTER_RASTER_TIME,

    /** The JavaScript frame time of a React Native view in nanoseconds. */
    JS_FRAME_TIME
}
