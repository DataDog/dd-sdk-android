/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.metric.interactiontonextview

import java.util.concurrent.TimeUnit

internal class TimeBasedInteractionIdentifier(
    private val timeThresholdInNanoSeconds: Long = TimeUnit.MILLISECONDS.toNanos(DEFAULT_TIME_THRESHOLD_MS)
) : LastInteractionIdentifier {

    override fun validate(context: PreviousViewLastInteractionContext): Boolean {
        return context.currentViewCreationTimestamp?.let { viewCreatedTime ->
            context.eventCreatedAtNanos - viewCreatedTime < timeThresholdInNanoSeconds
        } ?: false
    }

    companion object {
        internal const val DEFAULT_TIME_THRESHOLD_MS = 3000L
    }
}
