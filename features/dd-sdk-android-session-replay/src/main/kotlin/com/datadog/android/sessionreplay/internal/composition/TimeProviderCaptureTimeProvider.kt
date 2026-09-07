/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.internal.time.TimeProvider

internal class TimeProviderCaptureTimeProvider(
    private val timeProvider: TimeProvider
) : CaptureTimeProvider {
    override fun elapsedRealtimeNanos(): Long = timeProvider.getDeviceElapsedTimeNanos()
}
