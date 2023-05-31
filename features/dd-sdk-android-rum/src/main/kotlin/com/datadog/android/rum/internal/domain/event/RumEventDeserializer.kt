/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import com.datadog.android.v2.api.InternalLogger
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import java.util.Locale

internal class RumEventDeserializer(private val internalLogger: InternalLogger) :
    Deserializer<JsonObject, Any> {

    // region Deserializer

    override fun deserialize(model: JsonObject): Any? {
        return try {
            parseEvent(
                model.getAsJsonPrimitive(EVENT_TYPE_KEY_NAME)?.asString,
                model
            )
        } catch (e: JsonParseException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                DESERIALIZE_ERROR_MESSAGE_FORMAT.format(Locale.US, model),
                e
            )
            null
        } catch (e: IllegalStateException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                DESERIALIZE_ERROR_MESSAGE_FORMAT.format(Locale.US, model),
                e
            )
            null
        }
    }

    // endregion

    // region Internal

    @SuppressWarnings("ThrowingInternalException")
    @Throws(JsonParseException::class)
    private fun parseEvent(eventType: String?, model: JsonObject): Any {
        return when (eventType) {
            EVENT_TYPE_VIEW -> ViewEvent.fromJsonObject(model)
            EVENT_TYPE_RESOURCE -> ResourceEvent.fromJsonObject(model)
            EVENT_TYPE_ACTION -> ActionEvent.fromJsonObject(model)
            EVENT_TYPE_ERROR -> ErrorEvent.fromJsonObject(model)
            EVENT_TYPE_LONG_TASK -> LongTaskEvent.fromJsonObject(model)
            EVENT_TYPE_TELEMETRY -> {
                val status = model
                    .getAsJsonObject(EVENT_TELEMETRY_KEY_NAME)
                    .getAsJsonPrimitive(EVENT_TELEMETRY_STATUS_KEY_NAME)
                    .asString
                when (status) {
                    TELEMETRY_TYPE_DEBUG -> TelemetryDebugEvent.fromJsonObject(model)
                    TELEMETRY_TYPE_ERROR -> TelemetryErrorEvent.fromJsonObject(model)
                    else -> throw JsonParseException(
                        "We could not deserialize the telemetry event with status: $status"
                    )
                }
            }
            else -> throw JsonParseException(
                "We could not deserialize the event with type: $eventType"
            )
        }
    }

    // endregion

    companion object {
        const val EVENT_TYPE_KEY_NAME = "type"
        const val EVENT_TELEMETRY_KEY_NAME = "telemetry"
        const val EVENT_TELEMETRY_STATUS_KEY_NAME = "status"

        const val EVENT_TYPE_VIEW = "view"
        const val EVENT_TYPE_RESOURCE = "resource"
        const val EVENT_TYPE_ACTION = "action"
        const val EVENT_TYPE_ERROR = "error"
        const val EVENT_TYPE_LONG_TASK = "long_task"
        const val EVENT_TYPE_TELEMETRY = "telemetry"

        const val TELEMETRY_TYPE_DEBUG = "debug"
        const val TELEMETRY_TYPE_ERROR = "error"

        const val DESERIALIZE_ERROR_MESSAGE_FORMAT =
            "Error while trying to deserialize the serialized RumEvent: %s"
    }
}
