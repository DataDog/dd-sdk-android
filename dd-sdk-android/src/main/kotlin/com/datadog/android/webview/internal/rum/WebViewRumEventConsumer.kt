/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.domain.RumContext
import com.google.gson.JsonObject
import java.lang.IllegalStateException
import java.lang.NumberFormatException
import java.lang.UnsupportedOperationException
import kotlin.collections.LinkedHashMap

internal class WebViewRumEventConsumer(
    private val dataWriter: DataWriter<Any>,
    private val timeProvider: TimeProvider,
    private val webViewRumEventMapper: WebViewRumEventMapper = WebViewRumEventMapper(),
    private val contextProvider: WebViewRumEventContextProvider = WebViewRumEventContextProvider()
) {

    internal val offsets: LinkedHashMap<String, Long> = LinkedHashMap()
    fun consume(event: JsonObject) {
        // make sure we send a noop event to the RumSessionScope to refresh the session if needed
        GlobalRum.notifyIngestedWebViewEvent()
        val rumContext = contextProvider.getRumContext()
        val mappedEvent = map(event, rumContext)
        dataWriter.write(mappedEvent)
    }

    private fun map(
        event: JsonObject,
        rumContext: RumContext?
    ): JsonObject {
        try {
            val timeOffset = event.get(VIEW_KEY_NAME)?.asJsonObject?.get(VIEW_ID_KEY_NAME)
                ?.asString?.let { getOffset(it) } ?: 0L
            return webViewRumEventMapper.mapEvent(event, rumContext, timeOffset)
        } catch (e: ClassCastException) {
            sdkLogger.e(JSON_PARSING_ERROR_MESSAGE, e)
        } catch (e: NumberFormatException) {
            sdkLogger.e(JSON_PARSING_ERROR_MESSAGE, e)
        } catch (e: IllegalStateException) {
            sdkLogger.e(JSON_PARSING_ERROR_MESSAGE, e)
        } catch (e: UnsupportedOperationException) {
            sdkLogger.e(JSON_PARSING_ERROR_MESSAGE, e)
        }
        return event
    }

    private fun getOffset(viewId: String): Long {
        var offset = offsets[viewId]
        if (offset == null) {
            offset = timeProvider.getServerOffsetMillis()
            offsets[viewId] = offset
        }
        purgeOffsets()
        return offset
    }

    private fun purgeOffsets() {
        while (offsets.entries.size > MAX_VIEW_TIME_OFFSETS_RETAIN) {
            try {
                val viewId = offsets.entries.first()
                offsets.remove(viewId.key)
            } catch (e: NoSuchElementException) {
                // it should not happen but just in case.
                sdkLogger.e("Trying to remove from an empty map.", e)
                break
            }
        }
    }

    companion object {
        const val MAX_VIEW_TIME_OFFSETS_RETAIN = 3
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
