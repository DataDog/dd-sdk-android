/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.time

import com.datadog.android.internal.time.TimeProvider
import com.lyft.kronos.Clock
import java.util.concurrent.TimeUnit

internal class KronosTimeProvider(
    private val clock: Clock
) : TimeProvider {

    override fun getDeviceTimestamp(): Long {
        return System.currentTimeMillis()
    }

    override fun getServerTimestamp(): Long {
        return clock.getCurrentTimeMs()
    }

    override fun getServerOffsetMillis(): Long {
        val server = clock.getCurrentTimeMs()
        val device = System.currentTimeMillis()
        val delta = server - device
        return delta
    }

    override fun getServerOffsetNanos(): Long {
        return TimeUnit.MILLISECONDS.toNanos(getServerOffsetMillis())
    }
}
