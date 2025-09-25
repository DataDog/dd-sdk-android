/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.persistence

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.flags.featureflags.internal.model.FlagsStateEntry
import com.datadog.android.flags.featureflags.internal.model.JsonKeys
import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import org.json.JSONException
import org.json.JSONObject

/**
 * Serializer for converting FlagsStateEntry objects to JSON strings for datastore persistence.
 */
internal class FlagsStateSerializer(
    private val internalLogger: InternalLogger
) : Serializer<FlagsStateEntry> {

    @Suppress("TooGenericExceptionCaught")
    override fun serialize(model: FlagsStateEntry): String {
        return try {
            val json = JSONObject()

            val contextJson = JSONObject().apply {
                put(JsonKeys.TARGETING_KEY.value, model.evaluationContext.targetingKey)
                put(JsonKeys.ATTRIBUTES.value, serializeAttributes(model.evaluationContext.attributes))
            }
            json.put(JsonKeys.EVALUATION_CONTEXT.value, contextJson)

            val flagsJson = JSONObject()
            model.flags.forEach { (key, flag) ->
                flagsJson.put(key, serializePrecomputedFlag(flag))
            }
            json.put(JsonKeys.FLAGS.value, flagsJson)

            json.put(JsonKeys.LAST_UPDATE_TIMESTAMP.value, model.lastUpdateTimestamp)

            json.toString()
        } catch (e: JSONException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Failed to serialize FlagsStateEntry to JSON" },
                e
            )
            "{}"
        }
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // JSONObject operations wrapped in try-catch
    private fun serializeAttributes(attributes: Map<String, Any>): JSONObject {
        val attributesJson = JSONObject()
        attributes.forEach { (key, value) ->
            attributesJson.put(key, value)
        }
        return attributesJson
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // JSONObject operations wrapped in try-catch
    private fun serializePrecomputedFlag(flag: PrecomputedFlag): JSONObject {
        return JSONObject().apply {
            put(JsonKeys.VARIATION_TYPE.value, flag.variationType)
            put(JsonKeys.VARIATION_VALUE.value, flag.variationValue)
            put(JsonKeys.DO_LOG.value, flag.doLog)
            put(JsonKeys.ALLOCATION_KEY.value, flag.allocationKey)
            put(JsonKeys.VARIATION_KEY.value, flag.variationKey)
            put(JsonKeys.EXTRA_LOGGING.value, flag.extraLogging)
            put(JsonKeys.REASON.value, flag.reason)
        }
    }
}
