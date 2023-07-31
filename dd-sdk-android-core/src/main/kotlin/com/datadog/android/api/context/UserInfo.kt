/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.context

import com.datadog.android.core.internal.utils.JsonSerializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import kotlin.Any
import kotlin.Array
import kotlin.String
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

/**
 * Holds information about the current User.
 * @property id a unique identifier for the user, or null
 * @property name the name of the user, or null
 * @property email the email address of the user, or null
 * @property additionalProperties a dictionary of custom properties attached to the current user
 */
data class UserInfo(
    val id: String? = null,
    val name: String? = null,
    val email: String? = null,
    val additionalProperties: Map<String, Any?> = emptyMap()
) {

    @Suppress("StringLiteralDuplication")
    internal fun toJson(): JsonElement {
        val json = JsonObject()
        id?.let { idNonNull ->
            json.addProperty("id", idNonNull)
        }
        name?.let { nameNonNull ->
            json.addProperty("name", nameNonNull)
        }
        email?.let { emailNonNull ->
            json.addProperty("email", emailNonNull)
        }
        additionalProperties.forEach { (k, v) ->
            if (k !in RESERVED_PROPERTIES) {
                json.add(k, JsonSerializer.toJsonElement(v))
            }
        }
        return json
    }

    internal companion object {
        internal val RESERVED_PROPERTIES: Array<String> = arrayOf("id", "name", "email")

        @JvmStatic
        @Throws(JsonParseException::class)
        @Suppress("StringLiteralDuplication")
        fun fromJson(jsonString: String): UserInfo {
            try {
                // JsonParseException is declared in the method signature
                @Suppress("UnsafeThirdPartyFunctionCall")
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                return fromJsonObject(jsonObject)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type UserInfo",
                    e
                )
            }
        }

        @JvmStatic
        @Throws(JsonParseException::class)
        @Suppress("StringLiteralDuplication", "ThrowsCount")
        fun fromJsonObject(jsonObject: JsonObject): UserInfo {
            try {
                val id = jsonObject.get("id")?.asString
                val name = jsonObject.get("name")?.asString
                val email = jsonObject.get("email")?.asString
                val additionalProperties = mutableMapOf<String, Any?>()
                for (entry in jsonObject.entrySet()) {
                    if (entry.key !in RESERVED_PROPERTIES) {
                        additionalProperties[entry.key] = entry.value
                    }
                }
                return UserInfo(id, name, email, additionalProperties)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type UserInfo",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type UserInfo",
                    e
                )
            } catch (@Suppress("TooGenericExceptionCaught") e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type UserInfo",
                    e
                )
            }
        }
    }
}
