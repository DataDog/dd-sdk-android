/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.generated.DdSdkAndroidCoreLogger
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

internal class JsonObjectDeserializer(private val internalLogger: InternalLogger) :
    Deserializer<String, JsonObject> {

    private val logger = DdSdkAndroidCoreLogger(internalLogger)

    override fun deserialize(model: String): JsonObject? {
        return try {
            JsonParser.parseString(model).asJsonObject
        } catch (jpe: JsonParseException) {
            logger.logRumEventDeserializeError(model = model, throwable = jpe)
            null
        } catch (ise: IllegalStateException) {
            logger.logRumEventDeserializeError(model = model, throwable = ise)
            null
        }
    }
}
