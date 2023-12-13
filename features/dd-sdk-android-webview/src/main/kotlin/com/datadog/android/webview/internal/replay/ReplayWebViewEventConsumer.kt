/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.replay

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.webview.internal.WebViewEventConsumer
import com.datadog.android.webview.internal.rum.WebViewRumEventContextProvider
import com.datadog.android.webview.internal.rum.WebViewRumFeature
import com.google.gson.JsonObject

internal class ReplayWebViewEventConsumer(
    private val sdkCore: FeatureSdkCore,
    webViewId: Long,
    private val webViewReplayEventMapper: WebViewReplayEventMapper =
        WebViewReplayEventMapper(webViewId),
    private val contextProvider: WebViewRumEventContextProvider =
        WebViewRumEventContextProvider(sdkCore.internalLogger)
) :
    WebViewEventConsumer<JsonObject> {

    override fun consume(event: JsonObject) {
        sdkCore.getFeature(WebViewRumFeature.WEB_RUM_FEATURE_NAME)
            ?.withWriteContext { datadogContext, eventBatchWriter ->
                val rumContext = contextProvider.getRumContext(datadogContext)
                val timeOffset = getTimestampOffset()
                if (rumContext != null && rumContext.sessionState == "TRACKED") {
                    val mappedEvent = webViewReplayEventMapper.mapEvent(event, rumContext, timeOffset)
                    @Suppress("ThreadSafety") // inside worker thread context
                    sdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME)?.sendEvent(
                        mapOf(
                            "type" to WEB_VIEW_SESSION_REPLAY_RECORD,
                            "record" to mappedEvent.toString()
                        )
                    )
                }
            }
    }

    private fun getTimestampOffset(): Long {
        val rumFeatureContext = sdkCore.getFeatureContext(Feature.RUM_FEATURE_NAME)
        val timestampOffset = rumFeatureContext[RUM_VIEW_TIMESTAMP_OFFSET]
        return if (timestampOffset is Long) timestampOffset else 0L
    }

    companion object {
        const val WEB_VIEW_SESSION_REPLAY_RECORD = "web_view_session_replay_record"
        const val RECORD_TYPE = "record"
        val REPLAY_EVENT_TYPES = setOf(RECORD_TYPE)
        private const val RUM_VIEW_TIMESTAMP_OFFSET = "view_timestamp_offset"
    }
}
