/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.repository.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.ExperimentationOptOutMode
import com.datadog.android.flags.internal.evaluation.ExperimentationOptOut
import com.datadog.android.flags.internal.evaluation.wireValue
import com.datadog.android.flags.internal.model.AssignmentsResponse
import com.datadog.android.flags.internal.model.PrecomputedAssignments
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.serialization.FlagsJson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException

/**
 * Responsible for parsing network response to [PrecomputedFlag] objects.
 */
internal class PrecomputeMapper(
    private val internalLogger: InternalLogger,
    private val experimentationOptOutMode: ExperimentationOptOutMode = ExperimentationOptOutMode.DISABLED
) {
    internal fun map(
        rawJson: String,
        experimentationOptOut: ExperimentationOptOut.Request? = null
    ): PrecomputedAssignments? = try {
        val attributes = AssignmentsResponse.fromJson(rawJson).data.attributes
        val metadata = JsonObject().apply {
            attributes.additionalProperties.forEach { (key, value) ->
                add(key, value as? JsonElement)
            }
        }
        val options = metadata.get("evaluation_options")?.takeIf { it.isJsonObject }?.asJsonObject
        if (acknowledgesOptOut(options, experimentationOptOut)) {
            val obfuscation = FlagsJson.readObfuscation(metadata)
            val flags = attributes.flags.additionalProperties
            if (obfuscation != null) FlagsJson.validateKeys(flags.keys.iterator())
            PrecomputedAssignments(
                flags.mapValues { FlagsJson.readAssignment(FlagsJson.objectValue(it.value)) },
                obfuscation
            )
        } else {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { "The response does not match the requested experimentation opt-out policy." }
            )
            internalLogger.log(
                level = InternalLogger.Level.WARN,
                target = InternalLogger.Target.TELEMETRY,
                messageBuilder = {
                    "Rejected experimentation opt-out acknowledgement: mode=${experimentationOptOutMode.wireValue} " +
                        "policy=${if (experimentationOptOut == null) "none" else "applied"}"
                },
                onlyOnce = true
            )
            null
        }
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

    private fun acknowledgesOptOut(options: JsonObject?, request: ExperimentationOptOut.Request?): Boolean {
        val mode = options?.get("experimentation_opt_out_mode")
        val acknowledgedMode = mode?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        val legacy = options?.get("experimentation_opt_out")
        val acknowledged = legacy?.isJsonPrimitive == true && legacy.asJsonPrimitive.isBoolean && legacy.asBoolean
        return when (request?.mode) {
            null, ExperimentationOptOutMode.DISABLED ->
                !acknowledged &&
                    acknowledgedMode != ExperimentationOptOutMode.IMMEDIATE.wireValue &&
                    acknowledgedMode != ExperimentationOptOutMode.FUTURE_EXPERIMENTS_ONLY.wireValue
            ExperimentationOptOutMode.IMMEDIATE ->
                acknowledged &&
                    (mode == null || acknowledgedMode == request.mode.wireValue)
            ExperimentationOptOutMode.FUTURE_EXPERIMENTS_ONLY -> {
                val cutoff = options?.get(ExperimentationOptOut.ATTRIBUTE)
                acknowledgedMode == request.mode.wireValue && ExperimentationOptOut.timestampsMatch(
                    request.context.attributes[ExperimentationOptOut.ATTRIBUTE],
                    cutoff?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                )
            }
        }
    }

    private companion object {
        const val ERROR_FAILED_TO_PARSE_RESPONSE = "Failed to parse precomputed response"
    }
}
