/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.repository.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import org.json.JSONException
import org.json.JSONObject

/**
 * Responsible for parsing network response to [PrecomputedFlag] objects.
 */
internal class PrecomputeMapper(private val internalLogger: InternalLogger) {
    // JSONObject methods accept non-null String parameters despite Detekt's incorrect nullable interpretation
    // All getJsonObject calls are wrapped in try-catch for JSONException which is the actual exception thrown
    @Suppress("UnsafeThirdPartyFunctionCall")
    internal fun map(rawJson: String): Map<String, PrecomputedFlag> = try {
        val jsonResponse = JSONObject(rawJson)
        val data = jsonResponse.getJSONObject("data")
        val attributes = data.getJSONObject("attributes")
        val flags = attributes.getJSONObject("flags")

        val flagsMap = mutableMapOf<String, PrecomputedFlag>()

        val flagNames = flags.keys()
        while (flagNames.hasNext()) {
            val flagName = flagNames.next()
            val flagData = flags.getJSONObject(flagName)

            val precomputedFlag = PrecomputedFlag(
                variationType = flagData.getString("variationType"),
                variationValue = when (val value = flagData.get("variationValue")) {
                    is Boolean -> value.toString()
                    is String -> value
                    is Number -> value.toString()
                    else -> value.toString()
                },
                doLog = flagData.getBoolean("doLog"),
                allocationKey = flagData.getString("allocationKey"),
                variationKey = flagData.getString("variationKey"),
                extraLogging = flagData.optJSONObject("extraLogging") ?: JSONObject(),
                reason = flagData.getString("reason")
            )

            flagsMap[flagName] = precomputedFlag
        }

        flagsMap
    } catch (e: JSONException) {
        internalLogger.log(
            level = InternalLogger.Level.WARN,
            target = InternalLogger.Target.MAINTAINER,
            messageBuilder = { ERROR_FAILED_TO_PARSE_RESPONSE },
            throwable = e
        )

        emptyMap()
    }

    private companion object {
        const val ERROR_FAILED_TO_PARSE_RESPONSE = "Failed to parse precomputed response"
    }
}
