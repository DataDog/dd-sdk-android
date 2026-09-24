/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.trigger

import com.datadog.android.internal.profiling.ProfilingRumContext
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * A RUM OOM gating event received in-process, reduced to the few values needed to pair it with an
 * OS profiling trigger result that only gets delivered on a later application launch.
 *
 * Android's `ProfilingManager` does not guarantee that a registered OOM trigger fires before the
 * process dies: delivery can be deferred until the trigger is re-registered on the next launch.
 * RUM's own OOM detection, by contrast, always happens in-process. When the trigger result is
 * deferred, the RUM gating event would otherwise be lost with the dying process, leaving the
 * eventually-delivered trigger result with nothing to correlate against.
 *
 * @param rumErrorId id of the RUM error event describing the OOM.
 * @param timestampMs time the gating event was received, in milliseconds since epoch.
 * @param rumContext RUM context at the time of the OOM.
 */
internal data class PendingOomGatingEvent(
    val rumErrorId: String,
    val timestampMs: Long,
    val rumContext: ProfilingRumContext
) {

    fun toJson(): String {
        val json = JsonObject()
        json.addProperty(KEY_ERROR_ID, rumErrorId)
        json.addProperty(KEY_TIMESTAMP, timestampMs)
        json.addProperty(KEY_APPLICATION_ID, rumContext.applicationId)
        json.addProperty(KEY_SESSION_ID, rumContext.sessionId)
        rumContext.viewId?.let { json.addProperty(KEY_VIEW_ID, it) }
        rumContext.viewName?.let { json.addProperty(KEY_VIEW_NAME, it) }
        @Suppress("UnsafeThirdPartyFunctionCall") // only primitives were added
        return json.toString()
    }

    companion object {
        internal const val KEY_ERROR_ID = "error_id"
        internal const val KEY_TIMESTAMP = "timestamp"
        internal const val KEY_APPLICATION_ID = "application_id"
        internal const val KEY_SESSION_ID = "session_id"
        internal const val KEY_VIEW_ID = "view_id"
        internal const val KEY_VIEW_NAME = "view_name"

        /**
         * Rebuilds a [PendingOomGatingEvent] from [serialized], or returns `null` if it is not a
         * marker this version can use. The marker may have been written by a different version of
         * the SDK, or left half-written by the process death it exists to survive, so nothing
         * about its shape is assumed.
         */
        @Suppress("TooGenericExceptionCaught", "SwallowedException", "ReturnCount")
        fun fromJson(serialized: String): PendingOomGatingEvent? {
            return try {
                val json = JsonParser.parseString(serialized) as? JsonObject ?: return null
                PendingOomGatingEvent(
                    rumErrorId = json.string(KEY_ERROR_ID) ?: return null,
                    timestampMs = json.long(KEY_TIMESTAMP) ?: return null,
                    rumContext = ProfilingRumContext(
                        applicationId = json.string(KEY_APPLICATION_ID) ?: return null,
                        sessionId = json.string(KEY_SESSION_ID) ?: return null,
                        viewId = json.string(KEY_VIEW_ID),
                        viewName = json.string(KEY_VIEW_NAME)
                    )
                )
            } catch (e: Throwable) {
                null
            }
        }

        private fun JsonObject.string(key: String): String? {
            val primitive = getAsJsonPrimitive(key) ?: return null
            return if (primitive.isString) primitive.asString else null
        }

        private fun JsonObject.long(key: String): Long? {
            val primitive = getAsJsonPrimitive(key) ?: return null
            return if (primitive.isNumber) primitive.asLong else null
        }
    }
}
