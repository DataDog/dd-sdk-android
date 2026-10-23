/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.persistence

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.flags.internal.model.FlagKeyObfuscation
import com.datadog.android.flags.internal.model.FlagsState
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.serialization.FlagsJson
import com.google.gson.JsonParseException

/** Maps evaluator state to the generated disk-cache model. */
internal class FlagsStateSerializer(private val internalLogger: InternalLogger) : Serializer<FlagsStateEntry> {

    override fun serialize(model: FlagsStateEntry): String = try {
        val flags = FlagsState.Flags(
            model.flags.mapValues {
                FlagsJson.writeAssignment(it.value).toJson()
            }.toMutableMap()
        )
        val json = FlagsState(
            evaluationContext = FlagsState.EvaluationContext(
                targetingKey = model.evaluationContext.targetingKey,
                attributes = FlagsState.Flags(model.evaluationContext.attributes.toMutableMap())
            ),
            // Older SDKs must reject encoded caches instead of reading digests as original keys.
            flags = flags.takeIf { model.obfuscation == null },
            encodedFlags = flags.takeIf { model.obfuscation != null },
            lastUpdateTimestamp = model.lastUpdateTimestamp,
            observeFullEvaluationData = model.observeFullEvaluationData,
            obfuscated = true.takeIf { model.obfuscation != null },
            obfuscation = model.obfuscation?.let { FlagsState.Obfuscation(FlagKeyObfuscation.SCHEME, it.salt) }
        ).toJson().toString()
        json.escapeUnpairedSurrogates()
    } catch (e: JsonParseException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Failed to serialize FlagsStateEntry to JSON" },
            e
        )
        ""
    }

    private fun String.escapeUnpairedSurrogates(): String {
        val json = this
        if (json.none { it.isSurrogate() }) return json

        // JSON writers leave lone surrogates unescaped, and UTF-8 storage would replace them.
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

    private companion object {
        const val HEX_RADIX = 16
    }
}
