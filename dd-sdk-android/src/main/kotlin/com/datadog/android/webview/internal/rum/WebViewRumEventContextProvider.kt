/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.domain.RumContext

internal class WebViewRumEventContextProvider {

    private var rumFeatureDisabled = false

    fun getRumContext(): RumContext? {
        if (rumFeatureDisabled) {
            return null
        }
        val rumContext = GlobalRum.getRumContext()
        return if (
            rumContext.applicationId == RumContext.NULL_UUID ||
            rumContext.sessionId == RumContext.NULL_UUID
        ) {
            rumFeatureDisabled = true
            devLogger.w(RUM_NOT_INITIALIZED_WARNING_MESSAGE)
            sdkLogger.e(RUM_NOT_INITIALIZED_ERROR_MESSAGE)
            null
        } else {
            rumContext
        }
    }

    companion object {
        const val RUM_NOT_INITIALIZED_WARNING_MESSAGE = "You are trying to use the WebView " +
            "tracking API but the RUM feature was not properly initialized."
        const val RUM_NOT_INITIALIZED_ERROR_MESSAGE = "Trying to consume a bundled rum event" +
            " but the RUM feature was not yet initialized. Missing the RUM context."
    }
}
