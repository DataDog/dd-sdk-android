/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.processor

import com.datadog.android.sessionreplay.model.MobileSegment
import com.google.gson.JsonArray
import com.google.gson.JsonObject

internal data class EnrichedRecord(
    val applicationId: String,
    val sessionId: String,
    val viewId: String,
    val records: List<MobileSegment.MobileRecord>
) {

    fun toJson(): String {
        val json = JsonObject()
        json.addProperty(APPLICATION_ID_KEY, applicationId)
        json.addProperty(SESSION_ID_KEY, sessionId)
        json.addProperty(VIEW_ID_KEY, viewId)
        val recordsJsonArray = JsonArray()
        records.map { it.toJson() }
            .fold(recordsJsonArray) { acc, jsonElement ->
                acc.add(jsonElement)
                acc
            }
        json.add(RECORD_KEY, recordsJsonArray)
        return json.toString()
    }

    companion object {
        const val APPLICATION_ID_KEY = "application_id"
        const val SESSION_ID_KEY = "session_id"
        const val VIEW_ID_KEY = "view_id"
        const val RECORD_KEY = "records"
    }
}
