/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event.viewupdate

import com.datadog.android.rum.model.ViewEvent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Converts ViewEvent model objects to Map representation.
 *
 * This allows ViewEventTracker to compute diffs on ViewEvent objects
 * by converting them to a flexible Map<String, Any?> structure that
 * mirrors the JSON schema.
 */
internal object ViewEventConverter {

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, Any?>>() {}.type

    /**
     * Converts a ViewEvent to its Map representation.
     *
     * Uses JSON serialization/deserialization to extract all fields.
     * The resulting map structure matches the JSON schema.
     *
     * @param event The ViewEvent model object
     * @return Map representation suitable for diff computation
     */
    fun toMap(event: ViewEvent): Map<String, Any?> {
        return try {
            // Serialize to JSON then deserialize to Map
            // This ensures we get the exact structure that will be sent to backend
            val json = event.toJson().asJsonObject.toString()
            gson.fromJson(json, mapType)
        } catch (e: Exception) {
            // Fallback to empty map if conversion fails
            emptyMap()
        }
    }
}
