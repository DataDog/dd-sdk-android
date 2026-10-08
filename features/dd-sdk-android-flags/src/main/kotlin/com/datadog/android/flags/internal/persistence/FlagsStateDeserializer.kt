/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.persistence

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.flags.internal.model.FlagKeyObfuscation
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.model.PrecomputedFlagJson
import com.datadog.android.flags.internal.model.generated.FlagsState
import com.datadog.android.flags.model.EvaluationContext
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

internal class FlagsStateDeserializer(private val internalLogger: InternalLogger) :
    Deserializer<String, FlagsStateEntry> {

    override fun deserialize(model: String): FlagsStateEntry? = try {
        val json = PrecomputedFlagJson.objectValue(JsonParser.parseString(model))
        val obfuscation = FlagKeyObfuscation.read(json)
        // As before, only the map selected by the encoding descriptor is read.
        json.remove(if (obfuscation == null) "encodedFlags" else "flags")
        val contextJson = PrecomputedFlagJson.objectValue(json.get("evaluationContext"))
        if (contextJson.get("attributes") !is JsonObject) contextJson.remove("attributes")
        contextJson.get("targetingKey")?.let {
            contextJson.addProperty("targetingKey", PrecomputedFlagJson.stringValue(it))
        }
        if (json.get("lastUpdateTimestamp")?.isJsonPrimitive != true) {
            @Suppress("ThrowingInternalException") // Caught below and reported as an invalid cache.
            throw JsonParseException("Invalid persisted flag timestamp")
        }
        val state = FlagsState.fromJsonObject(json)
        val assignments = (if (obfuscation == null) state.flags else state.encodedFlags)?.additionalProperties
        if (assignments == null) {
            @Suppress("ThrowingInternalException") // Caught below and reported as an invalid cache.
            throw JsonParseException("Missing persisted assignments")
        }
        if (obfuscation != null) FlagKeyObfuscation.validateKeys(assignments.keys.iterator())
        val flags = assignments.mapNotNull { (key, value) ->
            // A malformed object can be skipped; a non-object entry invalidates the cache, as before.
            deserializePrecomputedFlag(PrecomputedFlagJson.objectValue(value))?.let { key to it }
        }.toMap()

        // A nonempty cache with no readable flags is not a valid empty configuration.
        if (flags.isEmpty() && assignments.isNotEmpty()) {
            null
        } else {
            FlagsStateEntry(
                evaluationContext = EvaluationContext(
                    state.evaluationContext.targetingKey,
                    state.evaluationContext.attributes?.additionalProperties.orEmpty().mapValues {
                        PrecomputedFlagJson.stringValue(it.value as JsonElement)
                    }
                ),
                flags = flags,
                lastUpdateTimestamp = state.lastUpdateTimestamp,
                obfuscation = obfuscation
            )
        }
    } catch (e: JsonParseException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Failed to deserialize FlagsStateEntry from JSON" },
            e
        )

        internalLogger.log(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.TELEMETRY,
            messageBuilder = { "Failed to parse persisted flag state" },
            throwable = e,
            onlyOnce = true
        )

        null
    }

    private fun deserializePrecomputedFlag(flagJson: JsonObject): PrecomputedFlag? = try {
        PrecomputedFlagJson.read(flagJson)
    } catch (e: JsonParseException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Failed to deserialize precomputed flag, skipping" },
            e
        )
        null
    }
}
