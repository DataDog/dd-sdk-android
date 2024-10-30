/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Time Bank is a concept representing an allocated execution quota per second. For example, if the quota is set to
 * 100 milliseconds per second, it means that within any given second, no more than 100 milliseconds can be used for
 * executing operations. If the full quota of 100 milliseconds has already been used within a second, further execution
 * is not permitted until the next second begins and the quota is recharged. Conversely, if less than 100 milliseconds
 * has been used and the second has not yet elapsed, execution may continue until the quota is reached.
 */
internal class RecordingTimeBank(
    private val maxTimeBalancePerSecondInMs: Long = DEFAULT_MAX_TIME_BALANCE_PER_SEC_IN_MS
) : TimeBank {

    // The normalized factor of balance increasing by time. If increasing 100ms balance in the bank takes 1000ms,
    // then the factor will be 100ms/1000ms = 0.1f
    private val balanceFactor = maxTimeBalancePerSecondInMs.toDouble() / TimeUnit.SECONDS.toMillis(1)

    @Volatile
    private var recordingTimeBalanceInNano = TimeUnit.MILLISECONDS.toNanos(maxTimeBalancePerSecondInMs)

    @Volatile
    private var lastCheckTime: Long = 0

    override fun consume(executionTime: Long) {
        recordingTimeBalanceInNano -= executionTime
    }

    override fun updateAndCheck(timestamp: Long): Boolean {
        increaseTimeBank(timestamp)
        lastCheckTime = timestamp
        return recordingTimeBalanceInNano >= 0
    }

    private fun increaseTimeBank(timestamp: Long) {
        val timePassedSinceLastExecution = timestamp - lastCheckTime
        recordingTimeBalanceInNano += (timePassedSinceLastExecution * balanceFactor).toLong()
        recordingTimeBalanceInNano =
            min(TimeUnit.MILLISECONDS.toNanos(maxTimeBalancePerSecondInMs), recordingTimeBalanceInNano)
    }

    companion object {
        private const val DEFAULT_MAX_TIME_BALANCE_PER_SEC_IN_MS = 100L
    }
}
