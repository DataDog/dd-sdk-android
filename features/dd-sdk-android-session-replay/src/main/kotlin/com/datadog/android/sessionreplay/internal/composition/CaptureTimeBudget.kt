/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

internal interface CaptureTimeBudget {
    fun canStart(timestampNs: Long): Boolean
    fun consume(durationNs: Long)

    companion object {
        val UNLIMITED: CaptureTimeBudget = UnlimitedCaptureTimeBudget()
    }
}

private class UnlimitedCaptureTimeBudget : CaptureTimeBudget {
    override fun canStart(timestampNs: Long): Boolean = true
    override fun consume(durationNs: Long) = Unit
}
