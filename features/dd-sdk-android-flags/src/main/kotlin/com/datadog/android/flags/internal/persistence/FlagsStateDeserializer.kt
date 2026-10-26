/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.persistence

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.serialization.FlagsJson
import com.datadog.android.flags.model.EvaluationContext
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

internal class FlagsStateDeserializer(private val internalLogger: InternalLogger) :
    Deserializer<String, FlagsStateEntry> {

    override fun deserialize(model: String): FlagsStateEntry? = try {
        val json = FlagsJson.objectValue(JsonParser.parseString(model))
        val obfuscation = FlagsJson.readObfuscation(json)
        val state = FlagsJson.readState(json, obfuscation)
        val assignments = (if (obfuscation == null) state.flags else state.encodedFlags)?.additionalProperties
        if (assignments == null) {
            @Suppress("ThrowingInternalException") // Caught below and reported as an invalid cache.
            throw JsonParseException("Missing persisted assignments")
        }
        if (obfuscation != null) FlagsJson.validateKeys(assignments.keys.iterator())
        val flags = assignments.mapNotNull { (key, value) ->
            // A malformed object can be skipped; a non-object entry invalidates the cache, as before.
            deserializePrecomputedFlag(FlagsJson.objectValue(value))?.let { key to it }
        }.toMap()

        // A nonempty cache with no readable flags is not a valid empty configuration.
        if (flags.isEmpty() && assignments.isNotEmpty()) {
            null
        } else {
            FlagsStateEntry(
                evaluationContext = EvaluationContext(
                    state.evaluationContext.targetingKey,
                    state.evaluationContext.attributes?.additionalProperties.orEmpty().mapValues {
                        FlagsJson.stringValue(it.value as JsonElement)
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
        FlagsJson.readAssignment(flagJson)
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
