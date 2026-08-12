/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.remote.model

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/**
 * Local bookkeeping for the remote configuration sync/apply telemetry described in the
 * "RFC - Remote Configuration Telemetry": the CDN metadata of the last fetched version, when it
 * was fetched, and when it was first applied.
 *
 * Persisted alongside the cached configuration content, and reported on the SDK's configuration
 * telemetry event once per session.
 *
 * @param configId identifier of the remote configuration bundle this metadata belongs to.
 * @param versionId CDN version identifier of the applied configuration (`x-amz-version-id`).
 * @param lastModified CDN publish timestamp of the applied configuration, in ms from epoch.
 * @param lastSynced device-local timestamp at which this version was fetched and cached, in ms from epoch.
 * @param firstApplied device-local timestamp at which this version was first observed as applied, in ms from
 * epoch. Stamped once, the first time a session observes this [versionId] as applied, and reused by every
 * subsequent session that runs on the same version — see the RFC for why this must stay stable rather than
 * being recomputed every session.
 * @param syncId identifier of the sync that produced this configuration version, used to deduplicate repeat
 * sessions from the same device without a persistent identifier.
 */
@Suppress("PackageNameVisibility") // Can't mark it as @InternalApi as it would apply to implementations as well
data class RemoteConfigSyncMetadata(
    val configId: String,
    val versionId: String?,
    val lastModified: Long?,
    val lastSynced: Long,
    val firstApplied: Long?,
    val syncId: String
) {

    /**
     * Serializes this metadata to a JSON string.
     */
    @Suppress("UnsafeThirdPartyFunctionCall") // safe: this class only has String/Long? properties
    fun toJsonString(): String = gson.toJson(this)

    companion object {
        @Suppress("UnsafeThirdPartyFunctionCall") // safe: Gson()'s default constructor doesn't throw
        private val gson = Gson()

        /**
         * Deserializes a [RemoteConfigSyncMetadata] from a JSON string.
         * @throws JsonSyntaxException if the given string is not valid JSON.
         */
        @Throws(JsonSyntaxException::class)
        @Suppress("UnsafeThirdPartyFunctionCall") // safe: wrapped in @Throws, caller catches JsonSyntaxException
        fun fromJson(jsonString: String): RemoteConfigSyncMetadata {
            return gson.fromJson(jsonString, RemoteConfigSyncMetadata::class.java)
        }
    }
}
