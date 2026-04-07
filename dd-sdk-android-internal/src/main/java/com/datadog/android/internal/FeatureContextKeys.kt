/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal

/**
 * Keys used to communicate profiling state via the features context.
 */
object FeatureContextKeys {
    /**
     * Indicates whether the profiler is currently running.
     * Written by the profiling feature and read by RUM and debug widget consumers.
     */
    const val PROFILER_IS_RUNNING: String = "profiler_is_running"
}
