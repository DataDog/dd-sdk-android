/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.repository.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.internal.model.FlagKeyObfuscation
import com.datadog.android.flags.internal.model.PrecomputedAssignments
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.model.PrecomputedFlagJson
import com.datadog.android.flags.internal.model.generated.AssignmentsResponse
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException

/**
 * Responsible for parsing network response to [PrecomputedFlag] objects.
 */
internal class PrecomputeMapper(private val internalLogger: InternalLogger) {
    internal fun map(rawJson: String): PrecomputedAssignments? = try {
        val attributes = AssignmentsResponse.fromJson(rawJson).data.attributes
        val metadata = JsonObject().apply {
            attributes.additionalProperties.forEach { (key, value) ->
                add(key, value as? JsonElement)
            }
        }
        val obfuscation = FlagKeyObfuscation.read(metadata)
        val flags = attributes.flags.additionalProperties
        if (obfuscation != null) FlagKeyObfuscation.validateKeys(flags.keys.iterator())
        PrecomputedAssignments(
            flags.mapValues { PrecomputedFlagJson.read(PrecomputedFlagJson.objectValue(it.value)) },
            obfuscation
        )
    } catch (e: JsonParseException) {
        internalLogger.log(
            level = InternalLogger.Level.WARN,
            target = InternalLogger.Target.MAINTAINER,
            messageBuilder = { ERROR_FAILED_TO_PARSE_RESPONSE },
            throwable = e
        )

        internalLogger.log(
            level = InternalLogger.Level.WARN,
            target = InternalLogger.Target.TELEMETRY,
            messageBuilder = { ERROR_FAILED_TO_PARSE_RESPONSE },
            throwable = e,
            onlyOnce = true
        )

        null
    }

    private companion object {
        const val ERROR_FAILED_TO_PARSE_RESPONSE = "Failed to parse precomputed response"
    }
}
