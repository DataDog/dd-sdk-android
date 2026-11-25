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
import com.datadog.android.flags.internal.model.SplitFeature
import com.datadog.android.flags.internal.model.VariationType
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
    private val warnedDestinations = mutableSetOf<String>()

    internal fun map(
        rawJson: String,
        experimentationOptOut: ExperimentationOptOut.Request? = null
    ): PrecomputedAssignments? = mapInternal(rawJson, experimentationOptOut, redactErrors = false)

    internal fun map(
        rawJson: String,
        experimentationOptOut: ExperimentationOptOut.Request?,
        redactErrors: Boolean
    ): PrecomputedAssignments? = mapInternal(rawJson, experimentationOptOut, redactErrors)

    private fun mapInternal(
        rawJson: String,
        experimentationOptOut: ExperimentationOptOut.Request?,
        redactErrors: Boolean
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
            val observeFullEvaluationData = FlagsJson.readConsent(metadata)
            val flags = attributes.flags.additionalProperties
            if (obfuscation != null) FlagsJson.validateKeys(flags.keys.iterator())
            val assignments = flags.mapValues { (_, value) ->
                FlagsJson.readAssignment(FlagsJson.objectValue(value)).copy(
                    observeFullEvaluationData = observeFullEvaluationData
                )
            }
            if (assignments.values.any { !VariationType.isSupported(it.variationType) }) warnUnsupportedType()
            warnUnknownDestinations(assignments.values)
            PrecomputedAssignments(assignments, obfuscation, observeFullEvaluationData)
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
        logParseFailure(e, redactErrors)
        null
    }

    private fun warnUnsupportedType() {
        internalLogger.log(
            level = InternalLogger.Level.WARN,
            target = InternalLogger.Target.USER,
            messageBuilder = {
                "A flag in the precomputed response has an unsupported variationType and resolves with PARSE_ERROR."
            },
            onlyOnce = true
        )
    }

    private fun logParseFailure(exception: JsonParseException, redactErrors: Boolean) {
        if (redactErrors) {
            // Parser exceptions can quote server-controlled response text. Never attach one for credentialed requests.
            internalLogger.log(
                level = InternalLogger.Level.WARN,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { ERROR_FAILED_TO_PARSE_RESPONSE }
            )
            internalLogger.log(
                level = InternalLogger.Level.WARN,
                target = InternalLogger.Target.TELEMETRY,
                messageBuilder = { ERROR_FAILED_TO_PARSE_RESPONSE },
                onlyOnce = true
            )
        } else {
            internalLogger.log(
                level = InternalLogger.Level.WARN,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { ERROR_FAILED_TO_PARSE_RESPONSE },
                throwable = exception
            )

            internalLogger.log(
                level = InternalLogger.Level.WARN,
                target = InternalLogger.Target.TELEMETRY,
                messageBuilder = { ERROR_FAILED_TO_PARSE_RESPONSE },
                throwable = exception,
                onlyOnce = true
            )
        }
    }

    private fun warnUnknownDestinations(flags: Collection<PrecomputedFlag>) {
        val unknown = mutableSetOf<String>()
        flags.forEach { flag ->
            flag.features.forEach { feature ->
                feature.destinations.forEach { if (it !in SplitFeature.KNOWN_DESTINATIONS) unknown.add(it) }
            }
        }
        if (unknown.isEmpty()) return
        val newDestinations = synchronized(warnedDestinations) {
            unknown.filter { warnedDestinations.size < MAX_WARNED_DESTINATIONS && warnedDestinations.add(it) }
        }
        if (newDestinations.isEmpty()) return
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            { "Ignoring unknown feature destinations: ${newDestinations.joinToString()}" }
        )
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.TELEMETRY,
            { "Ignoring unknown feature destinations" },
            onlyOnce = true
        )
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
        // Bounds the warnings a configuration can trigger.
        const val MAX_WARNED_DESTINATIONS = 64
        const val ERROR_FAILED_TO_PARSE_RESPONSE = "Failed to parse precomputed response"
    }
}
