/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.embedded.internal.rum

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature

internal class EmbeddedRumEventContextProvider(private val internalLogger: InternalLogger) {

    // Only gates the warning log -- the RUM context itself is still re-evaluated every call.
    private var rumNotInitializedWarningLogged = false

    @Suppress("ComplexCondition")
    fun getRumContext(datadogContext: DatadogContext): RumContext? {
        val rumContext = datadogContext.featuresContext[Feature.RUM_FEATURE_NAME]
        val rumApplicationId = rumContext?.get("application_id") as? String
        val rumSessionId = rumContext?.get("session_id") as? String
        val rumSessionState = rumContext?.get("session_state") as? String

        return if (rumApplicationId == null ||
            rumApplicationId == RumContext.NULL_UUID ||
            rumSessionId == null ||
            rumSessionId == RumContext.NULL_UUID ||
            rumSessionState.isNullOrBlank()
        ) {
            if (!rumNotInitializedWarningLogged) {
                rumNotInitializedWarningLogged = true
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { RUM_NOT_INITIALIZED_WARNING_MESSAGE }
                )
            }
            null
        } else {
            RumContext(rumApplicationId, rumSessionId, rumSessionState)
        }
    }

    companion object {
        const val RUM_NOT_INITIALIZED_WARNING_MESSAGE = "You are trying to use the embedded " +
            "Session Replay API but the RUM feature was not properly initialized."
    }
}
