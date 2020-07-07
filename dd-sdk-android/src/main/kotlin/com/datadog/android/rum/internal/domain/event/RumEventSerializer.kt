/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.core.internal.domain.Serializer
import com.datadog.android.rum.RumAttributes
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.util.Date

internal class RumEventSerializer : Serializer<RumEvent> {

    private val gson: Gson = Gson()

    // region Serializer

    override fun serialize(model: RumEvent): String {
        val json = gson.toJsonTree(model.event).asJsonObject

        // User Info
        addUserInfo(model, json)

        // Network Info
        addNetworkInfo(model, json)

        // custom attributes
        addCustomAttributes(model, json)

        return json.toString()
    }

    // endregion

    // region Internal

    private fun addUserInfo(model: RumEvent, root: JsonObject) {
        val email = model.userInfo?.email
        val id = model.userInfo?.id
        val name = model.userInfo?.name

        if (!email.isNullOrEmpty()) {
            root.addProperty(RumAttributes.USER_EMAIL, email)
        }
        if (!id.isNullOrEmpty()) {
            root.addProperty(RumAttributes.USER_ID, id)
        }
        if (!name.isNullOrEmpty()) {
            root.addProperty(RumAttributes.USER_NAME, name)
        }
    }

    private fun addNetworkInfo(model: RumEvent, root: JsonObject) {
        val info = model.networkInfo
        if (info != null) {
            root.addProperty(RumAttributes.NETWORK_CONNECTIVITY, info.connectivity.serialized)
            if (!info.carrierName.isNullOrBlank()) {
                root.addProperty(RumAttributes.NETWORK_CARRIER_NAME, info.carrierName)
            }
            if (info.carrierId >= 0) {
                root.addProperty(RumAttributes.NETWORK_CARRIER_ID, info.carrierId)
            }
            if (info.upKbps >= 0) {
                root.addProperty(RumAttributes.NETWORK_UP_KBPS, info.upKbps)
            }
            if (info.downKbps >= 0) {
                root.addProperty(RumAttributes.NETWORK_DOWN_KBPS, info.downKbps)
            }
            if (info.strength > Int.MIN_VALUE) {
                root.addProperty(RumAttributes.NETWORK_SIGNAL_STRENGTH, info.strength)
            }
        }
    }

    private fun addCustomAttributes(
        event: RumEvent,
        jsonEvent: JsonObject
    ) {
        event.attributes.forEach {
            val rawKey = it.key
            val key = if (rawKey in knownAttributes) rawKey else "context.$rawKey"
            val value = it.value
            jsonEvent.add(key, value.toJsonElement())
        }
    }

    // endregion

    companion object {
        internal val knownAttributes = setOf(
            RumAttributes.ACTION_GESTURE_DIRECTION,
            RumAttributes.ACTION_TARGET_PARENT_RESOURCE_ID,
            RumAttributes.ACTION_TARGET_PARENT_CLASSNAME,
            RumAttributes.ACTION_TARGET_PARENT_INDEX,
            RumAttributes.ACTION_TARGET_CLASS_NAME,
            RumAttributes.ACTION_TARGET_RESOURCE_ID,
            RumAttributes.ACTION_TARGET_TITLE,
            RumAttributes.ERROR_RESOURCE_METHOD,
            RumAttributes.ERROR_RESOURCE_STATUS_CODE,
            RumAttributes.ERROR_RESOURCE_URL
        )
    }
}

internal fun Any?.toJsonElement(): JsonElement {
    return when (this) {
        null -> JsonNull.INSTANCE
        is Boolean -> JsonPrimitive(this)
        is Int -> JsonPrimitive(this)
        is Long -> JsonPrimitive(this)
        is Float -> JsonPrimitive(this)
        is Double -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        is Date -> JsonPrimitive(this.time)
        is Iterable<*> -> this.toJsonArray()
        is JsonObject -> this
        is JsonArray -> this
        is JsonPrimitive -> this
        else -> JsonPrimitive(toString())
    }
}

internal fun Iterable<*>.toJsonArray(): JsonElement {
    val array = JsonArray()
    forEach {
        array.add(it.toJsonElement())
    }
    return array
}
