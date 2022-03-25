/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.rules

import android.content.Intent
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.telemetry.TelemetryPlaygroundActivity

internal class TelemetryTestRule(
    private val debugMessage: String,
    private val errorMessage: String
) : MockServerActivityTestRule<TelemetryPlaygroundActivity>(
    activityClass = TelemetryPlaygroundActivity::class.java,
    keepRequests = true,
    trackingConsent = TrackingConsent.GRANTED
) {
    override fun getActivityIntent(): Intent {
        return super.getActivityIntent().apply {
            putExtra(TelemetryPlaygroundActivity.TELEMETRY_DEBUG_MESSAGE_KEY, debugMessage)
            putExtra(TelemetryPlaygroundActivity.TELEMETRY_ERROR_MESSAGE_KEY, errorMessage)
        }
    }
}
