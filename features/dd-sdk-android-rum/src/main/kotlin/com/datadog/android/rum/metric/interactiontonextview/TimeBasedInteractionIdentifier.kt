/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.metric.interactiontonextview

import java.util.concurrent.TimeUnit

/**
 * A [LastInteractionIdentifier] that validates if the last interaction in the previous view
 * happened within a given time threshold. If the last interaction happened after the threshold,
 * the interaction is considered valid.
 * @param timeThresholdInMilliseconds The time threshold in milliseconds. Default is 3000ms.
 */
class TimeBasedInteractionIdentifier(
    timeThresholdInMilliseconds: Long = DEFAULT_TIME_THRESHOLD_MS
) : LastInteractionIdentifier {

    private val timeThresholdInNanoSeconds: Long = TimeUnit.MILLISECONDS.toNanos(timeThresholdInMilliseconds)

    override fun validate(context: PreviousViewLastInteractionContext): Boolean {
        return context.currentViewCreationTimestamp?.let { viewCreatedTime ->
            context.eventCreatedAtNanos - viewCreatedTime < timeThresholdInNanoSeconds
        } ?: false
    }

    companion object {
        internal const val DEFAULT_TIME_THRESHOLD_MS = 3000L
    }
}
