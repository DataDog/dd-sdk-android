/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.metric.interactiontonextview

import java.util.concurrent.TimeUnit

/**
 * A [LastInteractionIdentifier] that identifies the last action in the previous view as last interaction based
 * on the time interval between the action timestamp and the moment the next view was created.
 * If the last action happened in the given time interval, it is considered as the last interaction.
 * @param timeThresholdInMilliseconds The time threshold in milliseconds. Default is 3000ms.
 */
class TimeBasedInteractionIdentifier(
    timeThresholdInMilliseconds: Long = DEFAULT_TIME_THRESHOLD_MS
) : LastInteractionIdentifier {

    private val timeThresholdInNanoSeconds: Long = TimeUnit.MILLISECONDS.toNanos(timeThresholdInMilliseconds)

    override fun validate(context: PreviousViewLastInteractionContext): Boolean {
        return context.currentViewCreationTimestamp?.let { viewCreatedTime ->
            viewCreatedTime - context.eventCreatedAtNanos < timeThresholdInNanoSeconds
        } ?: false
    }

    internal fun defaultThresholdUsed(): Boolean {
        return DEFAULT_TIME_THRESHOLD_MS == TimeUnit.NANOSECONDS.toMillis(timeThresholdInNanoSeconds)
    }

    // region Object

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TimeBasedInteractionIdentifier

        return timeThresholdInNanoSeconds == other.timeThresholdInNanoSeconds
    }

    override fun hashCode(): Int {
        return timeThresholdInNanoSeconds.hashCode()
    }

    // endregion

    companion object {
        internal const val DEFAULT_TIME_THRESHOLD_MS = 3000L
    }
}
