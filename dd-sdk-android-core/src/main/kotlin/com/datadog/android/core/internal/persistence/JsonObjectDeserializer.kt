/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence

import com.datadog.android.v2.api.InternalLogger
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.util.Locale

internal class JsonObjectDeserializer(private val internalLogger: InternalLogger) :
    Deserializer<String, JsonObject> {
    override fun deserialize(model: String): JsonObject? {
        return try {
            JsonParser.parseString(model).asJsonObject
        } catch (jpe: JsonParseException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { DESERIALIZE_ERROR_MESSAGE_FORMAT.format(Locale.US, model) },
                jpe
            )
            null
        } catch (ise: IllegalStateException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { DESERIALIZE_ERROR_MESSAGE_FORMAT.format(Locale.US, model) },
                ise
            )
            null
        }
    }

    companion object {
        const val DESERIALIZE_ERROR_MESSAGE_FORMAT =
            "Error while trying to deserialize the RumEvent: %s"
    }
}
