/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.utils

/**
 * First version of the `com.google.android.profiling` system package (mainline 2026-07) which no longer
 * records stack sampling traces without any stack sample. Version codes increase with each release.
 */
private const val EMPTY_STACK_SAMPLES_FIXED_MODULE_VERSION_CODE = 371020780L

/**
 * Whether the profiling system package version can record stack sampling traces without any stack sample.
 * An unknown version (0, e.g. the system package is not found) is not considered as affected.
 */
internal fun canProfilingModuleRecordNoStackSamples(versionCode: Long): Boolean {
    return versionCode > 0L && versionCode < EMPTY_STACK_SAMPLES_FIXED_MODULE_VERSION_CODE
}
