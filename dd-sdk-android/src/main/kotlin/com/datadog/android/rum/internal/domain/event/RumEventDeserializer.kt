/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.util.Locale

internal class RumEventDeserializer : Deserializer<Any> {

    // region Deserializer

    override fun deserialize(model: String): Any? {
        return try {
            val jsonObject = JsonParser.parseString(model).asJsonObject
            parseEvent(
                jsonObject.getAsJsonPrimitive(EVENT_TYPE_KEY_NAME)?.asString,
                model
            )
        } catch (e: JsonParseException) {
            sdkLogger.e(DESERIALIZE_ERROR_MESSAGE_FORMAT.format(Locale.US, model), e)
            null
        } catch (e: IllegalStateException) {
            sdkLogger.e(DESERIALIZE_ERROR_MESSAGE_FORMAT.format(Locale.US, model), e)
            null
        }
    }

    // endregion

    // region Internal

    @SuppressWarnings("ThrowingInternalException")
    @Throws(JsonParseException::class)
    private fun parseEvent(eventType: String?, jsonString: String): Any {
        return when (eventType) {
            EVENT_TYPE_VIEW -> ViewEvent.fromJson(jsonString)
            EVENT_TYPE_RESOURCE -> ResourceEvent.fromJson(jsonString)
            EVENT_TYPE_ACTION -> ActionEvent.fromJson(jsonString)
            EVENT_TYPE_ERROR -> ErrorEvent.fromJson(jsonString)
            EVENT_TYPE_LONG_TASK -> LongTaskEvent.fromJson(jsonString)
            else -> throw JsonParseException(
                "We could not deserialize the event with type: $eventType"
            )
        }
    }

    // endregion

    companion object {
        const val EVENT_TYPE_KEY_NAME = "type"

        const val EVENT_TYPE_VIEW = "view"
        const val EVENT_TYPE_RESOURCE = "resource"
        const val EVENT_TYPE_ACTION = "action"
        const val EVENT_TYPE_ERROR = "error"
        const val EVENT_TYPE_LONG_TASK = "long_task"
        const val DESERIALIZE_ERROR_MESSAGE_FORMAT =
            "Error while trying to deserialize the serialized RumEvent: %s"
    }
}
