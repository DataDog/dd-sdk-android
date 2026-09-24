/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.utils

import com.datadog.android.internal.time.TimeProvider
import java.util.concurrent.TimeUnit

/**
 * Returns the offset between NTP-corrected (server) time and the boot clock, in nanoseconds.
 *
 * Adding this offset to a [TimeProvider.getDeviceElapsedRealtimeNanos] timestamp yields the
 * NTP-corrected time at which that boot-clock timestamp occurred.
 */
internal fun TimeProvider.bootNtpOffsetNs(): Long =
    TimeUnit.MILLISECONDS.toNanos(getServerTimestampMillis()) - getDeviceElapsedRealtimeNanos()
