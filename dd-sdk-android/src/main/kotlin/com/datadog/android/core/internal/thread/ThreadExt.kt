/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.core.internal.utils.sdkLogger

/**
 * A utility method to perform a Thread.sleep() safely.
 * @return whether the thread was interrupted during the sleep
 */
internal fun sleepSafe(durationMs: Long): Boolean {
    try {
        Thread.sleep(durationMs)
        return false
    } catch (e: InterruptedException) {
        try {
            // Restore the interrupted status
            Thread.currentThread().interrupt()
        } catch (se: SecurityException) {
            // this should not happen
            sdkLogger.e("Thread was unable to set its own interrupted state", se)
        }
        return true
    } catch (e: IllegalArgumentException) {
        // This means we tried sleeping for a negative time
        sdkLogger.w("Thread tried to sleep for a negative amount of time", e)
        return false
    }
}
