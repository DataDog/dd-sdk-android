/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.time

import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.BaseTimeProvider
import com.datadog.android.internal.time.TimeProvider
import com.lyft.kronos.Clock
import java.util.concurrent.TimeUnit

/**
 * A [TimeProvider] implementation that uses Kronos NTP for server time synchronization.
 *
 * Device timestamp and elapsed time are inherited from [BaseTimeProvider].
 */
internal class KronosTimeProvider(
    private val clock: Clock,
    private val internalLogger: InternalLogger
) : BaseTimeProvider() {

    override fun getServerTimestampMillis(): Long {
        return clock.safeGetCurrentTimeMs()
            .getOrElse { getDeviceTimestampMillis() }
    }

    override fun getServerOffsetMillis(): Long {
        return clock.safeGetCurrentTimeMs()
            .map { server ->
                val device = getDeviceTimestampMillis()
                val delta = server - device
                delta
            }
            .getOrDefault(0)
    }

    override fun getServerOffsetNanos(): Long {
        return TimeUnit.MILLISECONDS.toNanos(getServerOffsetMillis())
    }

    private fun Clock.safeGetCurrentTimeMs(): Result<Long> {
        return runCatching {
            getCurrentTimeMs()
        }.onFailure { ex ->
            internalLogger.log(
                level = InternalLogger.Level.WARN,
                targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                messageBuilder = { FAIL_MESSAGE },
                throwable = ex,
                onlyOnce = true,
                additionalProperties = emptyMap()
            )
        }
    }

    companion object {
        const val FAIL_MESSAGE = "KronosClock.getCurrentTimeMs failed with an exception"
    }
}
