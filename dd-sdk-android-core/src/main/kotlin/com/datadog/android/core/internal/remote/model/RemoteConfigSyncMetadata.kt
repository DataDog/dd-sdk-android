/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.remote.model

import com.datadog.android.lint.InternalApi
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlin.jvm.JvmStatic

/**
 * Local bookkeeping for the remote configuration sync/apply telemetry described in the
 * "RFC - Remote Configuration Telemetry": the CDN metadata of the last fetched version, when it
 * was fetched, and when it was first applied.
 *
 * Persisted alongside the cached configuration content as `<id>.metadata.json`, and reported
 * on the SDK's configuration telemetry event once per session.
 *
 * @param configId identifier of the remote configuration bundle this metadata belongs to.
 * @param versionId CDN version identifier of the applied configuration (`x-amz-version-id`).
 * @param lastModified CDN publish timestamp of the applied configuration, in ms from epoch.
 * @param lastSynced device-local timestamp at which this version was fetched and cached, in ms from epoch.
 * @param firstApplied device-local timestamp at which this version was first observed as applied, in ms
 * from epoch. Stamped once, the first time a session observes this [versionId] as applied, and reused
 * by every subsequent session that runs on the same version.
 * @param syncId identifier of the sync that produced this configuration version, used to deduplicate
 * repeat sessions from the same device without a persistent identifier.
 */
@InternalApi
data class RemoteConfigSyncMetadata(
    val configId: String,
    val versionId: String?,
    val lastModified: Long?,
    val lastSynced: Long,
    val firstApplied: Long?,
    val syncId: String
) {
    /**
     * Serializes this metadata to a JSON element.
     */
    fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("configId", configId)
        versionId?.let { json.addProperty("versionId", it) }
        lastModified?.let { json.addProperty("lastModified", it) }
        json.addProperty("lastSynced", lastSynced)
        firstApplied?.let { json.addProperty("firstApplied", it) }
        json.addProperty("syncId", syncId)
        return json
    }

    companion object {
        private const val PARSE_ERROR_MSG = "Unable to parse json into type RemoteConfigSyncMetadata"

        /**
         * Deserializes a [RemoteConfigSyncMetadata] from a JSON string.
         * @throws JsonParseException if the given string is not valid JSON or is missing required fields.
         */
        @JvmStatic
        @Throws(JsonParseException::class)
        fun fromJson(jsonString: String): RemoteConfigSyncMetadata {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                return fromJsonObject(jsonObject)
            } catch (e: IllegalStateException) {
                throw JsonParseException(PARSE_ERROR_MSG, e)
            } catch (e: JsonParseException) {
                throw JsonParseException(PARSE_ERROR_MSG, e)
            }
        }

        /**
         * Deserializes a [RemoteConfigSyncMetadata] from a [JsonObject].
         * @throws JsonParseException if the object is missing required fields or has invalid types.
         */
        @JvmStatic
        @Throws(JsonParseException::class)
        @Suppress("ThrowsCount")
        fun fromJsonObject(jsonObject: JsonObject): RemoteConfigSyncMetadata {
            try {
                // Required fields are guarded explicitly rather than relying on .asString/.asLong to
                // fail: those throw UnsupportedOperationException on an explicit JSON null, which is
                // easy to miss when reasoning about what this function can throw.
                val configId = jsonObject.get("configId")?.takeIf { !it.isJsonNull }?.asString
                    ?: throw JsonParseException("$PARSE_ERROR_MSG: missing configId")
                val versionId = jsonObject.get("versionId")?.takeIf { !it.isJsonNull }?.asString
                val lastModified = jsonObject.get("lastModified")?.takeIf { !it.isJsonNull }?.asLong
                val lastSynced = jsonObject.get("lastSynced")?.takeIf { !it.isJsonNull }?.asLong
                    ?: throw JsonParseException("$PARSE_ERROR_MSG: missing lastSynced")
                val firstApplied = jsonObject.get("firstApplied")?.takeIf { !it.isJsonNull }?.asLong
                val syncId = jsonObject.get("syncId")?.takeIf { !it.isJsonNull }?.asString
                    ?: throw JsonParseException("$PARSE_ERROR_MSG: missing syncId")
                return RemoteConfigSyncMetadata(
                    configId = configId,
                    versionId = versionId,
                    lastModified = lastModified,
                    lastSynced = lastSynced,
                    firstApplied = firstApplied,
                    syncId = syncId
                )
            } catch (e: IllegalStateException) {
                throw JsonParseException(PARSE_ERROR_MSG, e)
            } catch (e: NumberFormatException) {
                throw JsonParseException(PARSE_ERROR_MSG, e)
            } catch (e: UnsupportedOperationException) {
                // Thrown by .asString/.asLong when a field holds a non-primitive value (object/array)
                throw JsonParseException(PARSE_ERROR_MSG, e)
            }
        }
    }
}
