/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.trigger

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Durable record of what happened to the OOM profile of a held RUM error, kept until RUM
 * acknowledges the outcome: it never expires, as RUM keeps a reserved error waiting for it.
 * [State.ATTACHED] is only recorded once the profile batch write returned.
 *
 * @param rumErrorId id of the held RUM error.
 * @param state outcome of the OOM profile.
 * @param resultFilePath trace file of the profile, deleted when an [State.IN_FLIGHT] write is found
 * abandoned by a dead process.
 */
internal data class OomProfileOutcomeMarker(
    val rumErrorId: String,
    val state: State,
    val resultFilePath: String?
) {

    internal enum class State {
        IN_FLIGHT,
        ATTACHED,
        NOT_ATTACHED
    }

    fun toJson(): String {
        val json = JsonObject()
        json.addProperty(KEY_ERROR_ID, rumErrorId)
        json.addProperty(KEY_STATE, state.name)
        resultFilePath?.let { json.addProperty(KEY_PATH, it) }
        @Suppress("UnsafeThirdPartyFunctionCall") // only primitives were added
        return json.toString()
    }

    companion object {
        private const val KEY_ERROR_ID = "error_id"
        private const val KEY_STATE = "state"
        private const val KEY_PATH = "path"

        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        fun fromJson(serialized: String): OomProfileOutcomeMarker? {
            return try {
                val json = JsonParser.parseString(serialized).asJsonObject
                OomProfileOutcomeMarker(
                    rumErrorId = json.get(KEY_ERROR_ID).asString,
                    state = State.valueOf(json.get(KEY_STATE).asString),
                    resultFilePath = json.get(KEY_PATH)?.asString
                )
            } catch (e: Exception) {
                // Written by another SDK version, or left half-written.
                null
            }
        }
    }
}
