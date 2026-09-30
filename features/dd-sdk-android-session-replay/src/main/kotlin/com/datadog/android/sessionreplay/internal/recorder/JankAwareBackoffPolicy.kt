/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

/**
 * Whether capture should back off because this device's own recent frames are janky - missing
 * their own deadline - as reported by [FrameHealthMonitor]. Independent of
 * [RecordingTimeBank]'s aggregate per-second budget check: this owns only the frame-health
 * signal itself, not the backoff/decay bookkeeping that reacts to it, which stays in
 * [Debouncer] and is shared with the time-bank signal.
 */
internal class JankAwareBackoffPolicy(
    internal val isEnabled: Boolean = false,
    private val frameHealthMonitor: FrameHealthMonitor? = null
) {
    /** Whether the frame-health signal currently allows a capture to proceed. */
    fun allowsCapture(): Boolean = !isEnabled || frameHealthMonitor?.isDegraded() != true
}
