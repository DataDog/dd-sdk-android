/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.resources

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

internal data class ResourceHashesEntry(
    val lastUpdateDateNs: Long,
    val resourceHashes: Set<String>
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        val lastUpdateDateAsString = lastUpdateDateNs.toString()
        val resourceHashesAsString = resourceHashes.joinToString(",")
        json.addProperty(LAST_UPDATE_DATE_KEY, lastUpdateDateAsString)
        json.addProperty(RESOURCE_HASHES_KEY, resourceHashesAsString)
        return json
    }

    internal companion object {
        @Throws(JsonParseException::class)
        @Suppress("ReturnCount")
        fun fromJson(jsonString: String): ResourceHashesEntry? {
            return try {
                @Suppress("UnsafeThirdPartyFunctionCall") // caught by the caller
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                val lastUpdateDate = jsonObject.get(LAST_UPDATE_DATE_KEY)?.asString?.toLong()
                    ?: return null
                val resourceHashes = jsonObject.get(RESOURCE_HASHES_KEY)?.asString?.split(",")?.toSet()
                    ?: return null
                return ResourceHashesEntry(
                    lastUpdateDateNs = lastUpdateDate,
                    resourceHashes = resourceHashes
                )
            } catch (e: NumberFormatException) {
                throwJsonParseException(e)
                null
            } catch (e: IllegalStateException) {
                throwJsonParseException(e)
                null
            }
        }

        @Throws(JsonParseException::class)
        @Suppress("ThrowingInternalException")
        private fun throwJsonParseException(e: Exception) {
            throw JsonParseException(
                DESERIALIZE_ERROR,
                e
            )
        }

        internal const val LAST_UPDATE_DATE_KEY = "last_update_date"
        internal const val RESOURCE_HASHES_KEY = "resources_hashes"
        private const val DESERIALIZE_ERROR = "Unable to parse json into type ResourceHashesEntry"
    }
}
