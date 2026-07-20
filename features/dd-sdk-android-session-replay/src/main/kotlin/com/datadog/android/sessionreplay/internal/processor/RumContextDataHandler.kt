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
            // Deliberately kept in (not removed after investigation) at the user's explicit
            // request — see the git history/PR discussion for the "full screen broken images"
            // investigation this was added for. android.util.Log rather than InternalLogger:
            // this is meant to be read straight off Logcat on a real device/app while
            // reproducing the issue, not routed through the SDK's own telemetry/user-facing
            // channels. Added to trace a specific symptom: a resource (e.g. a pixel-captured
            // icon) whose queueItem call lands here gets dropped — see
            // ResourceItemCreationHandler's own logging for why this used to be permanently
            // silent for that resourceId regardless of whether RUM context later became valid.
            android.util.Log.d(
                "DD_SessionReplay",
                "[RumContextDataHandler] createRumContextData: dropping item, invalid RUM " +
                    "context: $newRumContext"
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
