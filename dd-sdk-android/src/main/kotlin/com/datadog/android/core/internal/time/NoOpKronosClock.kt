/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.time

import android.os.SystemClock
import com.lyft.kronos.KronosClock
import com.lyft.kronos.KronosTime

internal class NoOpKronosClock : KronosClock {
    override fun getCurrentNtpTimeMs(): Long? {
        return null
    }

    override fun getCurrentTime(): KronosTime {
        return KronosTime(System.currentTimeMillis(), null)
    }

    override fun getElapsedTimeMs(): Long {
        return SystemClock.elapsedRealtime()
    }

    override fun shutdown() {
    }

    override fun sync(): Boolean {
        return true
    }

    override fun syncInBackground() {
    }
}
