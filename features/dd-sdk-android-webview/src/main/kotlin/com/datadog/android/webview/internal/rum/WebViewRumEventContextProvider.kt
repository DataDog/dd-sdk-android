/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.internal.rum.RumContext
import com.datadog.android.internal.rum.rumContext

internal class WebViewRumEventContextProvider(private val internalLogger: InternalLogger) {

    private var rumFeatureDisabled = false

    fun getRumContext(datadogContext: DatadogContext): RumContext? {
        if (rumFeatureDisabled) {
            return null
        }

        val rumContext = datadogContext.featuresContext[Feature.RUM_FEATURE_NAME]?.rumContext()
        return if (rumContext == null ||
            rumContext.applicationId == RumContext.NULL_UUID ||
            rumContext.sessionId == RumContext.NULL_UUID
        ) {
            rumFeatureDisabled = true
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { RUM_NOT_INITIALIZED_WARNING_MESSAGE }
            )
            null
        } else {
            rumContext
        }
    }

    companion object {
        const val RUM_NOT_INITIALIZED_WARNING_MESSAGE = "You are trying to use the WebView " +
            "tracking API but the RUM feature was not properly initialized."
    }
}
