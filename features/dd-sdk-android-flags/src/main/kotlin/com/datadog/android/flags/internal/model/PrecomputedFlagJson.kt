/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.model

import com.datadog.android.flags.internal.model.generated.Assignment
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import org.json.JSONObject

/** Converts the generated JSON model to the existing evaluator model. */
internal object PrecomputedFlagJson {
    @Throws(JsonParseException::class)
    fun read(json: JsonObject): PrecomputedFlag {
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
        json.addProperty("doLog", booleanValue.equals("true", ignoreCase = true))
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

    fun write(flag: PrecomputedFlag): Assignment = Assignment(
        variationType = flag.variationType,
        variationValue = flag.variationValue,
        doLog = flag.doLog,
        allocationKey = flag.allocationKey,
        variationKey = flag.variationKey,
        extraLogging = Assignment.ExtraLogging.fromJson(flag.extraLogging.toString()),
        reason = flag.reason,
        serialId = flag.serialId
    )

    fun stringValue(value: JsonElement): String =
        if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else value.toString()

    @Throws(JsonParseException::class)
    fun objectValue(value: Any?): JsonObject = (value as? JsonObject)
        ?: throw JsonParseException("Expected a JSON object")

    private val STRING_FIELDS = listOf("variationType", "variationValue", "allocationKey", "variationKey", "reason")
}
