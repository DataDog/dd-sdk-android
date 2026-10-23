/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.persistence

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.model.JsonKeys
import com.datadog.android.flags.internal.model.PrecomputedFlag
import org.json.JSONException
import org.json.JSONObject

/**
 * Serializer for converting FlagsStateEntry objects to JSON strings for datastore persistence.
 */
internal class FlagsStateSerializer(private val internalLogger: InternalLogger) : Serializer<FlagsStateEntry> {

    @Suppress("TooGenericExceptionCaught")
    override fun serialize(model: FlagsStateEntry): String = try {
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
        json.put(JsonKeys.OBSERVE_FULL_EVALUATION_DATA.value, model.observeFullEvaluationData)

        escapeUnpairedSurrogates(json.toString())
    } catch (e: JSONException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Failed to serialize FlagsStateEntry to JSON" },
            e
        )
        ""
    }

    private fun escapeUnpairedSurrogates(json: String): String {
        if (json.none { it.isSurrogate() }) return json

        // JSONObject leaves lone surrogates unescaped, and UTF-8 storage would replace them.
        return buildString(json.length) {
            json.forEachIndexed { index, character ->
                val isPaired = when {
                    character.isHighSurrogate() -> json.getOrNull(index + 1)?.isLowSurrogate() == true
                    character.isLowSurrogate() -> json.getOrNull(index - 1)?.isHighSurrogate() == true
                    else -> true
                }
                if (isPaired) {
                    append(character)
                } else {
                    append("\\u")
                    append(character.code.toString(HEX_RADIX))
                }
            }
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
    private fun serializePrecomputedFlag(flag: PrecomputedFlag): JSONObject = JSONObject().apply {
        put(JsonKeys.VARIATION_TYPE.value, flag.variationType)
        put(JsonKeys.VARIATION_VALUE.value, flag.variationValue)
        put(JsonKeys.DO_LOG.value, flag.doLog)
        put(JsonKeys.ALLOCATION_KEY.value, flag.allocationKey)
        put(JsonKeys.VARIATION_KEY.value, flag.variationKey)
        put(JsonKeys.EXTRA_LOGGING.value, flag.extraLogging)
        put(JsonKeys.REASON.value, flag.reason)
        flag.serialId?.let { put(JsonKeys.SERIAL_ID.value, it) }
    }

    private companion object {
        private const val HEX_RADIX = 16
    }
}
