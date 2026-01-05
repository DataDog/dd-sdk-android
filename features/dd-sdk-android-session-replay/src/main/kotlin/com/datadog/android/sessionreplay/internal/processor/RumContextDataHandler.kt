/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import androidx.annotation.MainThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import java.util.Locale

internal class RumContextDataHandler(
    private val rumContextProvider: RumContextProvider,
    private val timeProvider: TimeProvider,
    private val internalLogger: InternalLogger
) {

    @MainThread
    internal fun createRumContextData(): RecordedQueuedItemContext? {
        val timestamp = timeProvider.getDeviceTimestampMillis()

        val newRumContext = rumContextProvider.getRumContext()

        if (newRumContext.isNotValid()) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                {
                    INVALID_RUM_CONTEXT_ERROR_MESSAGE_FORMAT.format(
                        Locale.ENGLISH,
                        newRumContext.toString()
                    )
                }
            )
            return null
        }

        return RecordedQueuedItemContext(timestamp + newRumContext.viewTimeOffsetMs, newRumContext.copy())
    }

    companion object {
        const val INVALID_RUM_CONTEXT_ERROR_MESSAGE_FORMAT = "SR RumContextDataHandler: Invalid RUM " +
            "context: [%s] when trying to bundle the RumContextData"
    }
}
