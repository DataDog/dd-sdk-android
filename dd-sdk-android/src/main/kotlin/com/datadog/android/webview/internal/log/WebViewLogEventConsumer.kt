/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.log

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.internal.utils.errorWithTelemetry
import com.datadog.android.webview.internal.WebViewEventConsumer
import com.datadog.android.webview.internal.rum.WebViewRumEventContextProvider
import com.google.gson.JsonObject

internal class WebViewLogEventConsumer(
    private val userLogsWriter: DataWriter<JsonObject>,
    private val rumContextProvider: WebViewRumEventContextProvider,
    private val timeProvider: TimeProvider,
    coreFeature: CoreFeature
) : WebViewEventConsumer<Pair<JsonObject, String>> {

    private val ddTags: String by lazy {
        "${LogAttributes.APPLICATION_VERSION}:${coreFeature.packageVersionProvider.version}" +
            ",${LogAttributes.ENV}:${coreFeature.envName}"
    }

    @WorkerThread
    override fun consume(event: Pair<JsonObject, String>) {
        map(event.first).let {
            if (event.second == USER_LOG_EVENT_TYPE) {
                userLogsWriter.write(it)
            }
        }
    }

    private fun map(event: JsonObject): JsonObject {
        addDdTags(event)
        correctDate(event)
        val rumContext = rumContextProvider.getRumContext()
        if (rumContext != null) {
            event.addProperty(LogAttributes.RUM_APPLICATION_ID, rumContext.applicationId)
            event.addProperty(LogAttributes.RUM_SESSION_ID, rumContext.sessionId)
        }
        return event
    }

    private fun correctDate(event: JsonObject) {
        try {
            event.get(DATE_KEY_NAME)?.asLong?.let {
                event.addProperty(
                    DATE_KEY_NAME,
                    it + timeProvider.getServerOffsetMillis()
                )
            }
        } catch (e: ClassCastException) {
            sdkLogger.errorWithTelemetry(JSON_PARSING_ERROR_MESSAGE, e)
        } catch (e: IllegalStateException) {
            sdkLogger.errorWithTelemetry(JSON_PARSING_ERROR_MESSAGE, e)
        } catch (e: NumberFormatException) {
            sdkLogger.errorWithTelemetry(JSON_PARSING_ERROR_MESSAGE, e)
        } catch (e: UnsupportedOperationException) {
            sdkLogger.errorWithTelemetry(JSON_PARSING_ERROR_MESSAGE, e)
        }
    }

    private fun addDdTags(event: JsonObject) {
        var eventDdTags: String? = null
        try {
            eventDdTags = event.get(DDTAGS_KEY_NAME)?.asString
        } catch (e: ClassCastException) {
            sdkLogger.errorWithTelemetry(JSON_PARSING_ERROR_MESSAGE, e)
        } catch (e: IllegalStateException) {
            sdkLogger.errorWithTelemetry(JSON_PARSING_ERROR_MESSAGE, e)
        } catch (e: UnsupportedOperationException) {
            sdkLogger.errorWithTelemetry(JSON_PARSING_ERROR_MESSAGE, e)
        }
        if (eventDdTags.isNullOrEmpty()) {
            event.addProperty(DDTAGS_KEY_NAME, ddTags)
        } else {
            event.addProperty(DDTAGS_KEY_NAME, ddTags + DDTAGS_SEPARATOR + eventDdTags)
        }
    }

    companion object {
        const val DDTAGS_SEPARATOR = ","
        const val DDTAGS_KEY_NAME = "ddtags"
        const val DATE_KEY_NAME = "date"
        const val USER_LOG_EVENT_TYPE = "log"
        const val INTERNAL_LOG_EVENT_TYPE = "internal_log"
        const val JSON_PARSING_ERROR_MESSAGE = "The bundled web log event could not be deserialized"
        val LOG_EVENT_TYPES = setOf(USER_LOG_EVENT_TYPE, INTERNAL_LOG_EVENT_TYPE)
    }
}
