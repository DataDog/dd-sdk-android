/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import com.datadog.android.core.configuration.UploadSchedulerStrategy
import com.datadog.android.core.internal.configuration.DataUploadConfiguration
import com.datadog.android.core.internal.data.upload.DataOkHttpUploader.Companion.HTTP_ACCEPTED
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

internal class DefaultUploadSchedulerStrategy(
    internal val uploadConfiguration: DataUploadConfiguration
) : UploadSchedulerStrategy {

    private val currentDelays = ConcurrentHashMap<String, Long>()

    // region UploadSchedulerStrategy

    override fun getMsDelayUntilNextUpload(
        featureName: String,
        uploadAttempts: Int,
        lastStatusCode: Int?,
        throwable: Throwable?
    ): Long {
        val previousDelay = currentDelays.getOrPut(featureName) { uploadConfiguration.defaultDelayMs }
        val updatedDelay = if (uploadAttempts > 0 && throwable == null && lastStatusCode == HTTP_ACCEPTED) {
            decreaseInterval(previousDelay)
        } else {
            increaseInterval(previousDelay, throwable)
        }
        currentDelays[featureName] = updatedDelay
        return updatedDelay
    }

    // endregion

    // region Internal

    private fun decreaseInterval(previousDelay: Long): Long {
        @Suppress("UnsafeThirdPartyFunctionCall") // not a NaN
        val newDelayMs = (previousDelay * DECREASE_PERCENT).roundToLong()
        return max(uploadConfiguration.minDelayMs, newDelayMs)
    }

    private fun increaseInterval(previousDelay: Long, throwable: Throwable?): Long {
        @Suppress("UnsafeThirdPartyFunctionCall") // not a NaN
        val newDelayMs = (previousDelay * INCREASE_PERCENT).roundToLong()

        return if (throwable is IOException) {
            // An IOException can mean a DNS error, or network connection loss
            // Those aren't likely to be a fluke or flakiness, so we use a longer delay to avoid infinite looping
            // and prevent battery draining
            NETWORK_ERROR_DELAY_MS
        } else {
            min(uploadConfiguration.maxDelayMs, newDelayMs)
        }
    }

    // endregion

    companion object {
        internal const val DECREASE_PERCENT = 0.90
        internal const val INCREASE_PERCENT = 1.10
        internal val NETWORK_ERROR_DELAY_MS = TimeUnit.MINUTES.toMillis(1) // 1 minute delay
    }
}
