/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.webview.internal.WebViewEventConsumer
import com.datadog.android.webview.internal.replay.WebViewReplayEventConsumer
import com.datadog.android.webview.internal.rum.domain.RumContext
import com.google.gson.JsonObject

internal class WebViewRumEventConsumer(
    private val sdkCore: FeatureSdkCore,
    internal val dataWriter: DataWriter<JsonObject>,
    internal val offsetProvider: TimestampOffsetProvider,
    private val webViewRumEventMapper: WebViewRumEventMapper,
    private val contextProvider: WebViewRumEventContextProvider =
        WebViewRumEventContextProvider(sdkCore.internalLogger)

) : WebViewEventConsumer<JsonObject> {

    @WorkerThread
    override fun consume(event: JsonObject) {
        // make sure we send a noop event to the RumSessionScope to refresh the session if needed
        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.sendEvent(
            mapOf(
                "type" to "web_view_ingested_notification"
            )
        )
        sdkCore.getFeature(WebViewRumFeature.WEB_RUM_FEATURE_NAME)
            ?.withWriteContext(
                withFeatureContexts = setOf(Feature.RUM_FEATURE_NAME, Feature.SESSION_REPLAY_FEATURE_NAME)
            ) { datadogContext, writeScope ->
                val rumContext = contextProvider.getRumContext(datadogContext)
                if (rumContext != null && rumContext.sessionState == "TRACKED") {
                    val sessionReplayFeatureContext = datadogContext.featuresContext[
                        Feature.SESSION_REPLAY_FEATURE_NAME
                    ]
                    val sessionReplayEnabled = sessionReplayFeatureContext?.get(
                        WebViewReplayEventConsumer.SESSION_REPLAY_ENABLED_KEY
                    ) as? Boolean ?: false
                    writeScope {
                        val mappedEvent = map(event, datadogContext, rumContext, sessionReplayEnabled)
                        dataWriter.write(it, mappedEvent, EventType.DEFAULT)
                    }
                }
            }
    }

    private fun map(
        event: JsonObject,
        datadogContext: DatadogContext,
        rumContext: RumContext?,
        sessionReplayEnabled: Boolean
    ): JsonObject {
        try {
            val timeOffset = event.get(VIEW_KEY_NAME)?.asJsonObject?.get(VIEW_ID_KEY_NAME)
                ?.asString?.let { offsetProvider.getOffset(it, datadogContext) } ?: 0L
            return webViewRumEventMapper.mapEvent(event, rumContext, timeOffset, sessionReplayEnabled)
        } catch (e: ClassCastException) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { JSON_PARSING_ERROR_MESSAGE },
                e
            )
        } catch (e: NumberFormatException) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { JSON_PARSING_ERROR_MESSAGE },
                e
            )
        } catch (e: IllegalStateException) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { JSON_PARSING_ERROR_MESSAGE },
                e
            )
        } catch (e: UnsupportedOperationException) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { JSON_PARSING_ERROR_MESSAGE },
                e
            )
        }
        return event
    }

    companion object {
        const val VIEW_EVENT_TYPE = "view"
        const val ACTION_EVENT_TYPE = "action"
        const val RESOURCE_EVENT_TYPE = "resource"
        const val ERROR_EVENT_TYPE = "error"
        const val LONG_TASK_EVENT_TYPE = "long_task"
        const val RUM_EVENT_TYPE = "rum"
        const val VIEW_KEY_NAME = "view"
        const val VIEW_ID_KEY_NAME = "id"
        const val JSON_PARSING_ERROR_MESSAGE = "The bundled web RUM event could not be deserialized"
        val RUM_EVENT_TYPES = setOf(
            VIEW_EVENT_TYPE,
            ACTION_EVENT_TYPE,
            RESOURCE_EVENT_TYPE,
            LONG_TASK_EVENT_TYPE,
            ERROR_EVENT_TYPE,
            RUM_EVENT_TYPE
        )
    }
}
