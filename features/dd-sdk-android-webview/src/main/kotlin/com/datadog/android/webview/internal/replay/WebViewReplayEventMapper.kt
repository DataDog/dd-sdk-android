/*
 * Unless explicitly stated otherwise all files in event repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.replay

import com.datadog.android.webview.internal.rum.domain.RumContext
import com.google.gson.JsonObject
import java.lang.ClassCastException
import java.lang.IllegalStateException
import java.lang.NumberFormatException

internal class WebViewReplayEventMapper {

    @Throws(
        ClassCastException::class,
        IllegalStateException::class,
        NumberFormatException::class
    )
    fun mapEvent(
        event: JsonObject,
        rumContext: RumContext,
        timeOffset: Long
    ): JsonObject {
        event.get("event")?.asJsonObject?.let { record ->
            record.get("timestamp")?.let { timestamp ->
                val asLong = timestamp.asLong
                val correctedTimestamp = asLong + timeOffset
                record.addProperty("timestamp", correctedTimestamp)
            }
            // we add this property here just for this POC to be abel to distinguish between
            // records type in the check_sr_output.py script and plot them separately
            record.addProperty("is_browser_record", true)
            event.addProperty("applicationId", rumContext.applicationId)
            event.addProperty("sessionId", rumContext.sessionId)
        }
        return event
    }
}
