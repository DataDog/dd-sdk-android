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
 * A trigger-captured out-of-memory profile that could not be uploaded before the process died,
 * reduced to the few values needed to upload it on the next application launch.
 *
 * Everything here is captured at OOM time and must stay small: writing it must not read the
 * trace file or allocate anything sizeable, because the heap is already exhausted.
 *
 * @param resultFilePath path of the trace file produced by the platform profiler.
 * @param startMs start of the capture, in milliseconds since epoch.
 * @param endMs end of the capture, in milliseconds since epoch.
 * @param bootNtpNs offset between NTP-corrected time and boot time, in nanoseconds, as measured
 * at capture time. It is persisted rather than recomputed because a reboot between the OOM and
 * the replay would make a freshly computed offset meaningless for these trace timestamps.
 * @param rumErrorId id of the RUM error event describing the OOM.
 * @param rumContext RUM context at the time of the OOM.
 */
internal data class PendingOomProfile(
    val resultFilePath: String,
    val startMs: Long,
    val endMs: Long,
    val bootNtpNs: Long,
    val rumErrorId: String,
    val rumContext: ProfilingRumContext
) {

    fun toJson(): String {
        val json = JsonObject()
        json.addProperty(KEY_PATH, resultFilePath)
        json.addProperty(KEY_START, startMs)
        json.addProperty(KEY_END, endMs)
        json.addProperty(KEY_BOOT_NTP, bootNtpNs)
        json.addProperty(KEY_ERROR_ID, rumErrorId)
        json.addProperty(KEY_APPLICATION_ID, rumContext.applicationId)
        json.addProperty(KEY_SESSION_ID, rumContext.sessionId)
        rumContext.viewId?.let { json.addProperty(KEY_VIEW_ID, it) }
        rumContext.viewName?.let { json.addProperty(KEY_VIEW_NAME, it) }
        @Suppress("UnsafeThirdPartyFunctionCall") // only primitives were added
        return json.toString()
    }

    companion object {
        internal const val KEY_PATH = "path"
        internal const val KEY_START = "start"
        internal const val KEY_END = "end"
        internal const val KEY_BOOT_NTP = "boot_ntp"
        internal const val KEY_ERROR_ID = "error_id"
        internal const val KEY_APPLICATION_ID = "application_id"
        internal const val KEY_SESSION_ID = "session_id"
        internal const val KEY_VIEW_ID = "view_id"
        internal const val KEY_VIEW_NAME = "view_name"

        /**
         * Rebuilds a [PendingOomProfile] from [serialized], or returns `null` if it is not a
         * marker this version can use. The marker may have been written by a different version
         * of the SDK, or left half-written by the process death it exists to survive, so nothing
         * about its shape is assumed.
         */
        @Suppress("TooGenericExceptionCaught", "SwallowedException", "ReturnCount")
        fun fromJson(serialized: String): PendingOomProfile? {
            return try {
                val json = JsonParser.parseString(serialized) as? JsonObject ?: return null
                PendingOomProfile(
                    resultFilePath = json.string(KEY_PATH) ?: return null,
                    startMs = json.long(KEY_START) ?: return null,
                    endMs = json.long(KEY_END) ?: return null,
                    bootNtpNs = json.long(KEY_BOOT_NTP) ?: return null,
                    rumErrorId = json.string(KEY_ERROR_ID) ?: return null,
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
