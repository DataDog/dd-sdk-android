/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.serialization

import com.datadog.android.flags.internal.model.Assignment
import com.datadog.android.flags.internal.model.FlagKeyObfuscation
import com.datadog.android.flags.internal.model.FlagsState
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import org.json.JSONObject

/** Generated model adapters, legacy JSON conversions, and encoding validation. */
internal object FlagsJson {
    @Throws(JsonParseException::class)
    fun readAssignment(json: JsonObject): PrecomputedFlag {
        // Preserve JSONObject's string conversion, including JSON variants and explicit null.
        STRING_FIELDS.forEach { field ->
            val value = json.get(field) ?: throw JsonParseException("Missing assignment field: $field")
            json.addProperty(field, stringValue(value))
        }
        val doLog = json.get("doLog")
        val booleanValue = doLog?.takeIf { it.isJsonPrimitive }?.asString
        if (!booleanValue.equals("true", ignoreCase = true) && !booleanValue.equals("false", ignoreCase = true)) {
            throw JsonParseException("Invalid assignment doLog")
        }
        // Gson accepts the same boolean strings; reject other values before it coerces them to false.
        // Existing readers ignore null and nonnumeric serial IDs.
        val serialId = json.get("serialId")
        if (serialId?.isJsonPrimitive != true || !serialId.asJsonPrimitive.isNumber) json.remove("serialId")

        val assignment = Assignment.fromJsonObject(json)

        @Suppress("UnsafeThirdPartyFunctionCall") // Generated JSON is a valid object.
        val extraLogging = JSONObject(assignment.extraLogging.toJson().toString())
        return PrecomputedFlag(
            variationType = assignment.variationType,
            variationValue = assignment.variationValue,
            doLog = assignment.doLog,
            allocationKey = assignment.allocationKey,
            variationKey = assignment.variationKey,
            extraLogging = extraLogging,
            reason = assignment.reason,
            serialId = assignment.serialId
        )
    }

    fun writeAssignment(flag: PrecomputedFlag): Assignment = Assignment(
        variationType = flag.variationType,
        variationValue = flag.variationValue,
        doLog = flag.doLog,
        allocationKey = flag.allocationKey,
        variationKey = flag.variationKey,
        extraLogging = Assignment.ExtraLogging.fromJson(flag.extraLogging.toString()),
        reason = flag.reason,
        serialId = flag.serialId
    )

    @Throws(JsonParseException::class)
    fun readState(json: JsonObject, obfuscation: FlagKeyObfuscation?): FlagsState {
        // Preserve old caches: ignore the unused map and accept JSONObject's context conversions.
        json.remove(if (obfuscation == null) "encodedFlags" else "flags")
        val context = objectValue(json.get("evaluationContext"))
        if (context.get("attributes") !is JsonObject) context.remove("attributes")
        context.get("targetingKey")?.let { context.addProperty("targetingKey", stringValue(it)) }
        if (json.get("lastUpdateTimestamp")?.isJsonPrimitive != true) {
            @Suppress("ThrowingInternalException") // Caught by the cache deserializer.
            throw JsonParseException("Invalid persisted flag timestamp")
        }
        return FlagsState.fromJsonObject(json)
    }

    @Throws(JsonParseException::class)
    @Suppress("ThrowsCount") // Report each invalid encoding field before generated readers can coerce it.
    fun readObfuscation(json: JsonObject): FlagKeyObfuscation? {
        val obfuscated = json.get("obfuscated")
        val isBoolean = obfuscated?.isJsonPrimitive == true && obfuscated.asJsonPrimitive.isBoolean
        val isPlaintext = obfuscated == null || (isBoolean && !obfuscated.asBoolean)
        if (isPlaintext && !json.has("obfuscation")) return null
        if (!isBoolean || !obfuscated.asBoolean) {
            @Suppress("ThrowingInternalException") // Caught by the response or cache deserializer.
            throw JsonParseException("Invalid flag-key obfuscation metadata")
        }
        val descriptor = objectValue(json.get("obfuscation"))
        // Generated string readers coerce primitives. The encoding contract requires JSON strings.
        listOf("scheme", "salt").forEach { field ->
            val value = descriptor.get(field)
            if (value?.isJsonPrimitive != true || !value.asJsonPrimitive.isString) {
                @Suppress("ThrowingInternalException") // Caught by the response or cache deserializer.
                throw JsonParseException("Invalid flag-key obfuscation $field")
            }
        }
        val encoding = FlagsState.Obfuscation.fromJsonObject(descriptor)
        if (encoding.scheme != FlagKeyObfuscation.SCHEME) {
            @Suppress("ThrowingInternalException") // Caught by the response or cache deserializer.
            throw JsonParseException("Unsupported flag-key obfuscation scheme")
        }
        return FlagKeyObfuscation.fromSalt(encoding.salt)
    }

    @Throws(JsonParseException::class)
    fun validateKeys(keys: Iterator<String>) {
        keys.forEach {
            if (it.length != DIGEST_HEX_LENGTH ||
                it.any { character -> character !in '0'..'9' && character !in 'a'..'f' }
            ) {
                @Suppress("ThrowingInternalException") // Caught by the response or cache deserializer.
                throw JsonParseException("Invalid encoded flag key")
            }
        }
    }

    fun stringValue(value: JsonElement): String =
        if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else value.toString()

    @Throws(JsonParseException::class)
    fun objectValue(value: Any?): JsonObject = (value as? JsonObject)
        ?: throw JsonParseException("Expected a JSON object")

    private val STRING_FIELDS = listOf("variationType", "variationValue", "allocationKey", "variationKey", "reason")
    private const val DIGEST_HEX_LENGTH = 64
}
