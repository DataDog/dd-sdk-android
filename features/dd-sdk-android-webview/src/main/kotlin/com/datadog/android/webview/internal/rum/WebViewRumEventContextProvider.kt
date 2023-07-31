/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.webview.internal.rum.domain.RumContext

internal class WebViewRumEventContextProvider(private val internalLogger: InternalLogger) {

    private var rumFeatureDisabled = false

    @Suppress("ComplexCondition")
    fun getRumContext(datadogContext: DatadogContext): RumContext? {
        if (rumFeatureDisabled) {
            return null
        }
        val rumContext = datadogContext.featuresContext[Feature.RUM_FEATURE_NAME]
        val rumApplicationId = rumContext?.get("application_id") as? String
        val rumSessionId = rumContext?.get("session_id") as? String
        return if (rumApplicationId == null ||
            rumApplicationId == RumContext.NULL_UUID ||
            rumSessionId == null ||
            rumSessionId == RumContext.NULL_UUID
        ) {
            rumFeatureDisabled = true
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { RUM_NOT_INITIALIZED_WARNING_MESSAGE }
            )
            null
        } else {
            RumContext(applicationId = rumApplicationId, sessionId = rumSessionId)
        }
    }

    companion object {
        const val RUM_NOT_INITIALIZED_WARNING_MESSAGE = "You are trying to use the WebView " +
            "tracking API but the RUM feature was not properly initialized."
    }
}
