/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Wraps the Session Replay records together with the related Rum Context.
 * Intended for internal usage.
 */
internal data class EnrichedRecord(
    val applicationId: String,
    val sessionId: String,
    val viewId: String,
    val records: List<JsonElement>
) {

    /**
     * Returns the JSON string equivalent of this object.
     */
    fun toJson(): String {
        val json = JsonObject()
        json.addProperty(APPLICATION_ID_KEY, applicationId)
        json.addProperty(SESSION_ID_KEY, sessionId)
        json.addProperty(VIEW_ID_KEY, viewId)
        val recordsJsonArray = records
            .fold(JsonArray()) { acc, jsonElement ->
                acc.add(jsonElement)
                acc
            }
        json.add(RECORDS_KEY, recordsJsonArray)
        return json.toString()
    }

    companion object {
        const val APPLICATION_ID_KEY: String = "application_id"
        const val SESSION_ID_KEY: String = "session_id"
        const val VIEW_ID_KEY: String = "view_id"
        const val RECORDS_KEY: String = "records"
    }
}
