/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.datadog.android.api.InternalLogger
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock

/**
 * Tries to acquire the lock within the given timeout and runs [block] if it succeeds, without
 * throwing if the lock cannot be acquired or the thread is interrupted while waiting.
 *
 * @param time Amount of time to wait for the lock.
 * @param unit Time unit of [time].
 * @param internalLogger Internal logger.
 * @param block Action to run once the lock is acquired.
 */
internal fun Lock.safeTryWithLock(time: Long, unit: TimeUnit, internalLogger: InternalLogger, block: () -> Unit) {
    val locked = try {
        // NullPointerException cannot happen, time unit is not null
        @Suppress("UnsafeThirdPartyFunctionCall")
        tryLock(time, unit)
    } catch (e: InterruptedException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            { "Couldn't acquire ${javaClass.simpleName} due to the exception thrown, aborting operation." },
            e
        )
        return
    }
    if (!locked) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            {
                "Couldn't acquire ${javaClass.simpleName} due to" +
                    " timeout ($time $unit), aborting operation."
            }
        )
        return
    }
    try {
        block()
    } finally {
        // IllegalMonitorStateException cannot happen, we returned early above if not locked
        @Suppress("UnsafeThirdPartyFunctionCall")
        unlock()
    }
}

/**
 * Acquires the lock and runs [block], without throwing if the thread is interrupted while
 * waiting for the lock.
 *
 * @param T Result type.
 * @param internalLogger Internal logger.
 * @param block Action to run once the lock is acquired.
 */
internal fun <T> Lock.safeWithLock(internalLogger: InternalLogger, block: () -> T): T? {
    try {
        lock()
    } catch (e: InterruptedException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            { "Couldn't acquire ${javaClass.simpleName} lock due to the exception thrown, aborting operation." },
            e
        )
        return null
    }
    return try {
        block()
    } finally {
        // IllegalMonitorStateException cannot happen, lock() call above succeeded
        @Suppress("UnsafeThirdPartyFunctionCall")
        unlock()
    }
}
