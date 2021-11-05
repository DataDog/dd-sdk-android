/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.webview

import com.datadog.android.core.internal.utils.sdkLogger
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.util.Locale.US

internal class WebEventConsumer(
    private val rumEventConsumer: RumEventConsumer,
    private val logsEventConsumer: LogsEventConsumer
) {
    fun consume(event: String) {
        try {
            val webEvent = JsonParser.parseString(event).asJsonObject
            if (!webEvent.has(EVENT_TYPE_KEY)) {
                sdkLogger.e(WEB_EVENT_MISSING_TYPE_ERROR_MESSAGE.format(US, event))
                return
            }
            if (!webEvent.has(EVENT_KEY)) {
                sdkLogger.e(WEB_EVENT_MISSING_WRAPPED_EVENT.format(US, event))
                return
            }

            val eventType = webEvent.get(EVENT_TYPE_KEY).asString
            val wrappedEvent = webEvent.get(EVENT_KEY).asJsonObject

            if (eventType == LOG_EVENT_TYPE) {
                logsEventConsumer.consume(wrappedEvent)
            } else {
                rumEventConsumer.consume(wrappedEvent, eventType)
            }
        } catch (e: JsonParseException) {
            sdkLogger.e(WEB_EVENT_PARSING_ERROR_MESSAGE.format(US, event), e)
        }
    }

    companion object {
        const val EVENT_TYPE_KEY = "eventType"
        const val EVENT_KEY = "event"
        const val EVENT_TAGS = "tags"

        const val LOG_EVENT_TYPE = "log"
        const val WEB_EVENT_PARSING_ERROR_MESSAGE = "We could not deserialize the" +
            " delegated browser event: %s."
        const val WEB_EVENT_MISSING_TYPE_ERROR_MESSAGE = "The web event: %s is missing" +
            " the event type."
        const val WEB_EVENT_MISSING_WRAPPED_EVENT = "The web event: %s is missing" +
            " the wrapped event object."
    }
}
