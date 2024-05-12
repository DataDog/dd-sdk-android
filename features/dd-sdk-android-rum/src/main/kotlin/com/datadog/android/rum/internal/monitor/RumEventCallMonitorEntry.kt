/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.monitor

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal data class RumEventCallMonitorEntry(
    val timePeriodStartTimeMs: AtomicLong = AtomicLong(System.currentTimeMillis()),
    var numCallsInTimePeriod: AtomicInteger = AtomicInteger(0)
)
