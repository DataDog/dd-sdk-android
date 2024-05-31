/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.replay

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.webview.internal.rum.TimestampOffsetProvider
import com.datadog.android.webview.internal.rum.domain.RumContext
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.lang.ClassCastException
import java.lang.IllegalStateException
import java.lang.NumberFormatException

internal class WebViewReplayEventMapper(
    private val webViewId: String,
    internal val offsetProvider: TimestampOffsetProvider
) {

    @Throws(
        ClassCastException::class,
        IllegalStateException::class,
        NumberFormatException::class
    )
    @SuppressWarnings("ThrowingInternalException")
    fun mapEvent(
        event: JsonObject,
        rumContext: RumContext,
        datadogContext: DatadogContext
    ): JsonObject {
        val viewDataObject = event.get(VIEW_OBJECT_KEY)?.asJsonObject
        val viewId = viewDataObject?.get(VIEW_ID_KEY)?.asString
            ?: throw IllegalStateException(BROWSER_EVENT_MISSING_VIEW_DATA_ERROR_MESSAGE)
        event.get(EVENT_KEY)?.asJsonObject?.let { record ->
            val timeOffset = offsetProvider.getOffset(viewId, datadogContext)
            record.get(TIMESTAMP_KEY)?.let { timestamp ->
                val asLong = timestamp.asLong
                val correctedTimestamp = asLong + timeOffset
                record.addProperty(TIMESTAMP_KEY, correctedTimestamp)
            }
            record.addProperty(SLOT_ID_KEY, webViewId)
            return bundleIntoEnrichedRecord(record, viewId, rumContext)
        } ?: throw IllegalStateException(BROWSER_EVENT_MISSING_RECORD_ERROR_MESSAGE)
    }

    private fun bundleIntoEnrichedRecord(
        record: JsonObject,
        browserViewId: String,
        rumContext: RumContext
    ): JsonObject {
        return JsonObject().apply {
            addProperty(ENRICHED_RECORD_APPLICATION_ID_KEY, rumContext.applicationId)
            addProperty(ENRICHED_RECORD_SESSION_ID_KEY, rumContext.sessionId)
            addProperty(ENRICHED_RECORD_VIEW_ID_KEY, browserViewId)
            add(RECORDS_KEY, JsonArray().apply { add(record) })
        }
    }

    companion object {
        const val EVENT_KEY = "event"
        const val VIEW_OBJECT_KEY = "view"
        const val VIEW_ID_KEY = "id"
        const val TIMESTAMP_KEY = "timestamp"
        const val ENRICHED_RECORD_APPLICATION_ID_KEY = "application_id"
        const val ENRICHED_RECORD_SESSION_ID_KEY = "session_id"
        const val ENRICHED_RECORD_VIEW_ID_KEY = "view_id"
        const val RECORDS_KEY = "records"
        const val SLOT_ID_KEY = "slotId"
        const val BROWSER_EVENT_MISSING_VIEW_DATA_ERROR_MESSAGE =
            "The bundled web Replay event does not contain the mandatory view data"
        const val BROWSER_EVENT_MISSING_RECORD_ERROR_MESSAGE =
            "The bundled web Replay event does not contain the record data"
    }
}
