/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.metric.networksettled

import java.util.concurrent.TimeUnit

internal class TimeBasedInitialResourceIdentifier(
    private val timeThresholdInNanoSeconds: Long = TimeUnit.MILLISECONDS.toNanos(DEFAULT_TIME_THRESHOLD_MS)
) : InitialResourceIdentifier {
    override fun validate(
        context: NetworkSettledResourceContext
    ): Boolean {
        return context.viewCreatedTimestamp?.let { viewCreatedTimestamp ->
            context.eventCreatedAtNanos - viewCreatedTimestamp < timeThresholdInNanoSeconds
        } ?: false
    }

    companion object {
        internal const val DEFAULT_TIME_THRESHOLD_MS = 100L
    }
}
