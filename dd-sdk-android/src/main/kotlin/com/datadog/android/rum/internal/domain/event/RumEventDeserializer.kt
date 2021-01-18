/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.core.internal.domain.Deserializer
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

internal class RumEventDeserializer : Deserializer<RumEvent> {

    // region Deserializer

    override fun deserialize(model: String): RumEvent? {
        return try {
            val jsonObject = JsonParser.parseString(model).asJsonObject
            val userAttributes: MutableMap<String, Any?> = mutableMapOf()
            val globalAttributes: MutableMap<String, Any?> = mutableMapOf()
            val customTimings: MutableMap<String, Long> = mutableMapOf()
            resolveAttributes(userAttributes, globalAttributes, customTimings, jsonObject)
            val resolvedCustomTimings = resolveCustomTimings(customTimings)
            val deserializedBundledEvent =
                fromJson(jsonObject.getAsJsonPrimitive(EVENT_TYPE_KEY_NAME)?.asString, model)
            RumEvent(
                deserializedBundledEvent,
                globalAttributes,
                userAttributes,
                resolvedCustomTimings
            )
        } catch (e: JsonParseException) {
            sdkLogger.e(
                "Error while trying to deserialize " +
                    "the serialized RumEvent: $model",
                e
            )
            null
        }
    }

    // endregion

    // region Internal

    private fun resolveCustomTimings(timings: MutableMap<String, Long>): MutableMap<String, Long>? {
        return if (timings.isNotEmpty()) {
            timings
        } else {
            null
        }
    }

    private fun resolveAttributes(
        userAttributes: MutableMap<String, Any?>,
        globalAttributes: MutableMap<String, Any?>,
        customTimings: MutableMap<String, Long>,
        jsonObject: JsonObject
    ) {
        val customGlobalAttributesPrefix = RumEventSerializer.GLOBAL_ATTRIBUTE_PREFIX + '.'
        val customUserAttributesPrefix = RumEventSerializer.USER_ATTRIBUTE_PREFIX + '.'
        val customTimingsAttributesPrefix =
            RumEventSerializer.VIEW_CUSTOM_TIMINGS_ATTRIBUTE_PREFIX + '.'
        val customGlobalAttributesPrefixLength = customGlobalAttributesPrefix.length
        val customUserAttributesPrefixLength = customUserAttributesPrefix.length
        val customTimingsAttributesPrefixLength = customTimingsAttributesPrefix.length

        jsonObject.keySet().forEach {
            when {
                it.startsWith(customUserAttributesPrefix) -> {
                    userAttributes[it.substring(customUserAttributesPrefixLength)] =
                        jsonObject.get(it)
                }
                it.startsWith(customGlobalAttributesPrefix) -> {
                    globalAttributes[it.substring(customGlobalAttributesPrefixLength)] =
                        jsonObject.get(it)
                }
                it.startsWith(customTimingsAttributesPrefix) -> {
                    customTimings[it.substring(customTimingsAttributesPrefixLength)] =
                        jsonObject.get(it).asLong
                }
            }
        }
    }

    @SuppressWarnings("ThrowingInternalException")
    @Throws(JsonParseException::class)
    private fun fromJson(eventType: String?, jsonString: String): Any {
        return when (eventType) {
            EVENT_TYPE_VIEW -> ViewEvent.fromJson(jsonString)
            EVENT_TYPE_RESOURCE -> ResourceEvent.fromJson(jsonString)
            EVENT_TYPE_ACTION -> ActionEvent.fromJson(jsonString)
            EVENT_TYPE_ERROR -> ErrorEvent.fromJson(jsonString)
            else -> throw JsonParseException(
                "We could not deserialize the " +
                    "event with type: $eventType"
            )
        }
    }

    // endregion

    companion object {
        const val EVENT_TYPE_KEY_NAME = "type"

        // Maybe we need to expose these as static constants in the POKOs from the Generator ??
        const val EVENT_TYPE_VIEW = "view"
        const val EVENT_TYPE_RESOURCE = "resource"
        const val EVENT_TYPE_ACTION = "action"
        const val EVENT_TYPE_ERROR = "error"
    }
}
