/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.metric.networksettled

import java.util.concurrent.TimeUnit

/**
 * An [InitialResourceIdentifier] that validates the initial resource based on the time elapsed.
 * The resource is considered initial if the time elapsed between creation of the view and the start of the resource
 * is less than the provided threshold in milliseconds.By default, the threshold is set to 100 milliseconds.
 * @param timeThresholdInMilliseconds The threshold in milliseconds.
 */
class TimeBasedInitialResourceIdentifier(
    timeThresholdInMilliseconds: Long = DEFAULT_TIME_THRESHOLD_MS
) : InitialResourceIdentifier {
    private val timeThresholdInNanoSeconds: Long = TimeUnit.MILLISECONDS.toNanos(timeThresholdInMilliseconds)

    override fun validate(
        context: NetworkSettledResourceContext
    ): Boolean {
        return context.viewCreatedTimestamp?.let { viewCreatedTimestamp ->
            context.eventCreatedAtNanos - viewCreatedTimestamp < timeThresholdInNanoSeconds
        } ?: false
    }

    // region Object

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TimeBasedInitialResourceIdentifier

        return timeThresholdInNanoSeconds == other.timeThresholdInNanoSeconds
    }

    override fun hashCode(): Int {
        return timeThresholdInNanoSeconds.hashCode()
    }

    // endregion

    companion object {
        internal const val DEFAULT_TIME_THRESHOLD_MS = 100L
    }
}
