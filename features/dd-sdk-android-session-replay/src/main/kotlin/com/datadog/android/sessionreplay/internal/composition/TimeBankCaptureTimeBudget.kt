/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.sessionreplay.internal.recorder.TimeBank

internal class TimeBankCaptureTimeBudget(
    private val timeBank: TimeBank,
    private val onAdmissionDenied: () -> Unit = {}
) : CaptureTimeBudget {
    override fun canStart(timestampNs: Long): Boolean = timeBank.updateAndCheck(timestampNs).also { admitted ->
        if (!admitted) onAdmissionDenied()
    }

    override fun consume(durationNs: Long) = timeBank.consume(durationNs)
}
