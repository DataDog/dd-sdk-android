/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.extension

import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.model.LogEvent

fun Int.asLogStatus(): LogEvent.Status {
    return when (this) {
        android.util.Log.ASSERT -> LogEvent.Status.CRITICAL
        android.util.Log.ERROR -> LogEvent.Status.ERROR
        android.util.Log.WARN -> LogEvent.Status.WARN
        android.util.Log.INFO -> LogEvent.Status.INFO
        android.util.Log.DEBUG -> LogEvent.Status.DEBUG
        android.util.Log.VERBOSE -> LogEvent.Status.TRACE
        Log.CRASH -> LogEvent.Status.EMERGENCY
        else -> LogEvent.Status.DEBUG
    }
}
