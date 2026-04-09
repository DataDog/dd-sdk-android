/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal

internal object RumContextKeys {
    // Must match RumContext.SESSION_SAMPLE_RATE in dd-sdk-android-rum.
    internal const val SESSION_SAMPLE_RATE = "session_sample_rate"
}
