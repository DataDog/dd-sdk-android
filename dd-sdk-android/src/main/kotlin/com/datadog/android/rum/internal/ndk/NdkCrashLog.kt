/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.ndk

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

internal data class NdkCrashLog(
    val signal: Int,
    val timestamp: Long,
    val signalName: String,
    val message: String,
    val stacktrace: String
) {

    internal fun toJson(): String {
        val jsonObject = JsonObject()
        jsonObject.addProperty(SIGNAL_KEY_NAME, signal)
        jsonObject.addProperty(SIGNAL_NAME_KEY_NAME, signalName)
        jsonObject.addProperty(TIMESTAMP_KEY_NAME, timestamp)
        jsonObject.addProperty(MESSAGE_KEY_NAME, message)
        jsonObject.addProperty(STACKTRACE_KEY_NAME, stacktrace)
        return jsonObject.toString()
    }

    companion object {

        internal const val SIGNAL_KEY_NAME = "signal"
        internal const val TIMESTAMP_KEY_NAME = "timestamp"
        internal const val MESSAGE_KEY_NAME = "message"
        internal const val SIGNAL_NAME_KEY_NAME = "signal_name"
        internal const val STACKTRACE_KEY_NAME = "stacktrace"

        @Throws(JsonParseException::class)
        internal fun fromJson(jsonString: String): NdkCrashLog {
            val jsonObject = JsonParser.parseString(jsonString).asJsonObject
            return NdkCrashLog(
                jsonObject.get(SIGNAL_KEY_NAME).asInt,
                jsonObject.get(TIMESTAMP_KEY_NAME).asLong,
                jsonObject.get(SIGNAL_NAME_KEY_NAME).asString,
                jsonObject.get(MESSAGE_KEY_NAME).asString,
                jsonObject.get(STACKTRACE_KEY_NAME).asString
            )
        }
    }
}
