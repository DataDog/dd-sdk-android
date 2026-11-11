/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.serialization

import com.datadog.android.flags.internal.model.PrecomputedAssignments
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.serialization.PortableFlagsConfiguration.Failure
import com.datadog.android.flags.internal.serialization.PortableFlagsConfiguration.Precomputed
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.IOException
import java.io.StringReader

/** Precomputed-only wire codec. Rules entries are not decoded or retained. */
internal class PortableFlagsConfigurationCodec {
    fun decode(wire: String): PortableFlagsConfiguration {
        val envelope = parseObject(wire)
        val version = envelope?.get("version")
        if (!isNumber(version) || version?.asDouble !in SUPPORTED_VERSIONS) {
            return PortableFlagsConfiguration(null, Failure.INVALID_ENVELOPE)
        }
        val entry = envelope?.get("precomputed")
        val precomputed = try {
            entry?.let { decodePrecomputed(FlagsJson.objectValue(it)) }
        } catch (_: JsonParseException) {
            null
        }
        val failure = if (entry != null && precomputed == null) Failure.INVALID_PRECOMPUTED else null
        return PortableFlagsConfiguration(precomputed, failure)
    }

    fun encode(configuration: PortableFlagsConfiguration): String = JsonObject().apply {
        val precomputed = configuration.precomputed
        addProperty("version", if (precomputed?.assignments?.obfuscation == null) 1 else 2)
        if (precomputed != null) {
            add(
                "precomputed",
                JsonObject().apply {
                    addProperty(RESPONSE, precomputed.response)
                    precomputed.context?.let { add(CONTEXT, it) }
                    precomputed.fetchedAt?.let { if (it.isFinite()) addProperty(FETCHED_AT, it) }
                    precomputed.etag?.let { addProperty(ETAG, it) }
                }
            )
        }
    }.toString()

    private fun decodePrecomputed(entry: JsonObject): Precomputed? {
        val response = entry.get(RESPONSE)
        if (!validEntry(entry)) return null
        val payload = parseObject(response.asString)
        val attributes = FlagsJson.objectValue(FlagsJson.objectValue(payload?.get("data")).get("attributes"))
        val createdAt = attributes.get("createdAt")
        return if (isString(createdAt) || isNumber(createdAt)) {
            val flags = FlagsJson.objectValue(attributes.get("flags"))
            val obfuscation = FlagsJson.readObfuscation(attributes)
            if (obfuscation != null) FlagsJson.validateKeys(flags.keySet().iterator())
            val consent = FlagsJson.readConsent(attributes)
            val assignments = mutableMapOf<String, PrecomputedFlag>()
            val errors = mutableMapOf<String, String>()
            flags.entrySet().forEach { (key, value) ->
                val assignment = readFlag(value)
                if (assignment == null) {
                    errors[key] = "Invalid precomputed flag configuration"
                } else {
                    assignments[key] = assignment.copy(observeFullEvaluationData = consent)
                }
            }
            Precomputed(
                response.asString,
                entry.get(CONTEXT) as? JsonObject,
                entry.get(FETCHED_AT)?.asDouble,
                entry.get(ETAG)?.asString,
                PrecomputedAssignments(assignments, obfuscation, consent),
                errors
            )
        } else {
            null
        }
    }

    private fun validEntry(entry: JsonObject): Boolean {
        val context = entry.get(CONTEXT)
        val fetchedAt = entry.get(FETCHED_AT)
        val etag = entry.get(ETAG)
        return isString(entry.get(RESPONSE)) && (fetchedAt == null || isNumber(fetchedAt)) &&
            (etag == null || isString(etag)) &&
            (context == null || (context is JsonObject && validContext(context)))
    }

    private fun readFlag(value: JsonElement): PrecomputedFlag? {
        val flag = value as? JsonObject
        val doLog = flag?.get("doLog")
        val variant = flag?.get("variationValue")
        val validDoLog = doLog?.isJsonPrimitive == true && doLog.asJsonPrimitive.isBoolean
        val validMetadata = flag?.let { STRING_FIELDS.all { key -> isString(it.get(key)) } } == true && validDoLog
        if (flag == null || variant == null || !validMetadata) {
            return null
        }
        return normalizedType(flag.get(VARIATION_TYPE).asString, variant)?.let { type ->
            try {
                // readAssignment coerces fields in place; this parsed tree is discarded after decoding.
                FlagsJson.readAssignment(
                    flag.apply {
                        addProperty(VARIATION_TYPE, type)
                        // Browser assignments may omit extraLogging; native assignments need finite JSON.
                        val extraLogging = get(EXTRA_LOGGING) as? JsonObject
                        add(EXTRA_LOGGING, extraLogging?.let { if (isJsonValue(it)) it else null } ?: JsonObject())
                    }
                )
            } catch (_: JsonParseException) {
                null
            }
        }
    }

    private fun normalizedType(type: String, value: JsonElement): String? = when (type) {
        "boolean", "BOOLEAN" -> if (value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) "boolean" else null
        "string", "STRING" -> if (isString(value)) "string" else null
        "number", "NUMBER", "NUMERIC", "INTEGER" -> if (isNumber(value)) "number" else null
        "integer", "float" -> if (isNumber(value)) type else null
        "object", "OBJECT", "JSON" -> if (isJsonValue(value)) "object" else null
        else -> null
    }

    private fun validContext(context: JsonObject): Boolean =
        (context.get("targetingKey")?.let { isString(it) } ?: true) && isJsonValue(context)

    // Bound nesting before accepted values reach recursive native adapters, copies, or serializers.
    private fun isJsonValue(root: JsonElement): Boolean {
        val pending = ArrayDeque<Pair<JsonElement, Int>>()
        pending.addLast(root to 0)
        while (true) {
            val (value, depth) = pending.removeFirstOrNull() ?: return true
            val invalidNumber = value.isJsonPrimitive && value.asJsonPrimitive.isNumber && !isNumber(value)
            if (depth > MAX_JSON_DEPTH || invalidNumber) {
                return false
            }
            when {
                value.isJsonArray -> value.asJsonArray.forEach { pending.addLast(it to depth + 1) }
                value.isJsonObject -> value.asJsonObject.entrySet().forEach { pending.addLast(it.value to depth + 1) }
            }
        }
    }

    private fun isString(value: JsonElement?): Boolean =
        value?.isJsonPrimitive == true && value.asJsonPrimitive.isString

    private fun isNumber(value: JsonElement?): Boolean =
        value?.isJsonPrimitive == true && value.asJsonPrimitive.isNumber &&
            value.asString.toDoubleOrNull()?.isFinite() == true

    private fun parseObject(raw: String): JsonObject? = try {
        JsonReader(StringReader(raw)).use { reader ->
            // Avoid JsonParser's lenient mode; Gson 2.10 still accepts some non-JSON syntax.
            val value = Gson().getAdapter(JsonElement::class.java).read(reader)
            if (reader.peek() == JsonToken.END_DOCUMENT) value as? JsonObject else null
        }
    } catch (_: JsonParseException) {
        null
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: NullPointerException) {
        null
    }

    private companion object {
        const val RESPONSE = "response"
        const val CONTEXT = "context"
        const val FETCHED_AT = "fetchedAt"
        const val ETAG = "etag"
        const val VARIATION_TYPE = "variationType"
        const val EXTRA_LOGGING = "extraLogging"
        const val MAX_JSON_DEPTH = 255
        val SUPPORTED_VERSIONS = setOf(1.0, 2.0)
        val STRING_FIELDS = listOf("allocationKey", "variationKey", VARIATION_TYPE, "reason")
    }
}
