/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.persistence

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.flags.featureflags.internal.model.FlagsStateEntry
import com.datadog.android.flags.featureflags.internal.model.JsonKeys
import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import com.datadog.android.flags.featureflags.model.EvaluationContext
import org.json.JSONException
import org.json.JSONObject

internal class FlagsStateDeserializer(
    private val internalLogger: InternalLogger
) : Deserializer<String, FlagsStateEntry> {

    override fun deserialize(model: String): FlagsStateEntry? {
        return try {
            val json = JSONObject(model)

            @Suppress("UnsafeThirdPartyFunctionCall") // JSONObject operations wrapped in try-catch
            val contextJson = json.getJSONObject(JsonKeys.EVALUATION_CONTEXT.value)
            val evaluationContext = deserializeEvaluationContext(contextJson)

            @Suppress("UnsafeThirdPartyFunctionCall") // JSONObject operations wrapped in try-catch
            val flagsJson = json.getJSONObject(JsonKeys.FLAGS.value)
            val flags = deserializeFlags(flagsJson)

            @Suppress("UnsafeThirdPartyFunctionCall") // JSONObject operations wrapped in try-catch
            val timestamp = json.getLong(JsonKeys.LAST_UPDATE_TIMESTAMP.value)

            FlagsStateEntry(
                evaluationContext = evaluationContext,
                flags = flags,
                lastUpdateTimestamp = timestamp
            )
        } catch (e: JSONException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Failed to deserialize FlagsStateEntry from JSON" },
                e
            )
            null
        }
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // JSONObject operations wrapped in try-catch
    private fun deserializeEvaluationContext(contextJson: JSONObject): EvaluationContext {
        val targetingKey = contextJson.getString(JsonKeys.TARGETING_KEY.value)

        val attributes = try {
            deserializeAttributes(contextJson.getJSONObject(JsonKeys.ATTRIBUTES.value))
        } catch (_: JSONException) {
            emptyMap()
        }

        return EvaluationContext(targetingKey, attributes)
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // JSONObject operations wrapped in try-catch
    private fun deserializeAttributes(attributesJson: JSONObject): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        val keys = attributesJson.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            val value = attributesJson.get(key).toString()
            attributes[key] = value
        }

        return attributes
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // JSONObject operations wrapped in try-catch
    private fun deserializeFlags(flagsJson: JSONObject): Map<String, PrecomputedFlag> {
        val flags = mutableMapOf<String, PrecomputedFlag>()
        val keys = flagsJson.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            val flagJson = flagsJson.getJSONObject(key)
            val flag = deserializePrecomputedFlag(flagJson)
            if (flag != null) {
                flags[key] = flag
            }
        }

        return flags
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // JSONObject operations wrapped in try-catch
    private fun deserializePrecomputedFlag(flagJson: JSONObject): PrecomputedFlag? {
        return try {
            PrecomputedFlag(
                variationType = flagJson.getString(JsonKeys.VARIATION_TYPE.value),
                variationValue = flagJson.getString(JsonKeys.VARIATION_VALUE.value),
                doLog = flagJson.getBoolean(JsonKeys.DO_LOG.value),
                allocationKey = flagJson.getString(JsonKeys.ALLOCATION_KEY.value),
                variationKey = flagJson.getString(JsonKeys.VARIATION_KEY.value),
                extraLogging = flagJson.getJSONObject(JsonKeys.EXTRA_LOGGING.value),
                reason = flagJson.getString(JsonKeys.REASON.value)
            )
        } catch (e: JSONException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Failed to deserialize precomputed flag, skipping" },
                e
            )
            null
        }
    }
}
