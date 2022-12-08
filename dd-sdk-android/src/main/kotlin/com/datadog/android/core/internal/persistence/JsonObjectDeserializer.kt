/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence

import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.internal.utils.errorWithTelemetry
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.util.Locale

internal class JsonObjectDeserializer : Deserializer<String, JsonObject> {
    override fun deserialize(model: String): JsonObject? {
        return try {
            JsonParser.parseString(model).asJsonObject
        } catch (jpe: JsonParseException) {
            sdkLogger.errorWithTelemetry(
                DESERIALIZE_ERROR_MESSAGE_FORMAT.format(Locale.US, model),
                jpe
            )
            null
        } catch (ise: IllegalStateException) {
            sdkLogger.errorWithTelemetry(
                DESERIALIZE_ERROR_MESSAGE_FORMAT.format(Locale.US, model),
                ise
            )
            null
        }
    }

    companion object {
        const val DESERIALIZE_ERROR_MESSAGE_FORMAT =
            "Error while trying to deserialize the serialized RumEvent: %s"
    }
}
