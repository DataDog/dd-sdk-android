/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.replay

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.webview.internal.WebViewEventConsumer

class ReplayWebViewEventConsumer(private val sdkCore: FeatureSdkCore) : WebViewEventConsumer<String> {

    override fun consume(event: String) {
        sdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME)?.sendEvent(
            mapOf(
                "type" to WEB_VIEW_SESSION_REPLAY_RECORD,
                "record" to event
            )
        )
    }

    companion object {
        const val WEB_VIEW_SESSION_REPLAY_RECORD = "web_view_session_replay_record"
        const val RECORD_TYPE = "record"
        val REPLAY_EVENT_TYPES = setOf(RECORD_TYPE)
    }
}
