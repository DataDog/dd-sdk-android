/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2026-Present Datadog, Inc.
 */

package com.datadog.flags.benchmark

import datadog.ffe.flagging.ufc.v1.Ufc
import java.security.MessageDigest

internal class EvaluationFailure(val code: String) : Exception(code)

internal data class EvaluationRequest(val flagKey: String, val fallback: Boolean, val context: Map<String, Any?>) {
    companion object {
        fun from(request: Map<String, Any?>): EvaluationRequest {
            require(request["type"] == "boolean") { "Only boolean benchmark requests are supported" }
            val key = request["flagKey"] as? String ?: error("Missing flagKey")
            val fallback = request["defaultValue"] as? Boolean ?: error("Invalid defaultValue")
            val context = request["context"] as? Map<*, *> ?: error("Invalid context")
            fun validate(value: Any?) {
                when (value) {
                    null, is String, is Boolean -> Unit
                    is Number -> require(value.toDouble().isFinite())
                    is List<*> -> value.forEach(::validate)
                    is Map<*, *> -> value.forEach { (key, item) -> require(key is String); validate(item) }
                    else -> error("Non-JSON context")
                }
            }
            validate(context)
            @Suppress("UNCHECKED_CAST")
            return EvaluationRequest(key, fallback, context as Map<String, Any?>)
        }
    }
}

/** Deliberately matches the Swift benchmark subset, not a production evaluator. */
internal class RulesEvaluator(val configuration: Ufc.FlagsConfiguration) {
    fun evaluate(request: EvaluationRequest, timestamp: Long = System.currentTimeMillis()): Map<String, Any?> {
        val metadata = linkedMapOf<String, Any?>(
            "__dd_eval_timestamp_ms" to timestamp,
            "__dd_observe_full_evaluation_data" to configuration.observeFullEvaluationData
        )
        fun fallback(reason: String, error: String? = null): MutableMap<String, Any?> = linkedMapOf<String, Any?>(
            "value" to request.fallback, "reason" to reason, "flagMetadata" to metadata
        ).also { if (error != null) it["errorCode"] = error }
        val flag = configuration.flagsMap[request.flagKey] ?: return fallback("ERROR", "FLAG_NOT_FOUND")
        return try {
            checkRule(flag.minimumFeatureLevel == 0 && flag.variationType == Ufc.VariationType.VARIATION_TYPE_BOOLEAN)
            val cache = mutableMapOf<Int, Boolean>()
            for (allocation in flag.allocationsList) {
                if (allocation.hasTargetingConditionIndex() &&
                    !matches(allocation.targetingConditionIndex, request.context, cache)) continue
                val coordinates = allocation.partitionKeyList.map { partition ->
                    when (partition.kindCase) {
                        Ufc.PartitionKey.KindCase.TIME -> timestamp.also { checkRule(it in 0..MAX_SAFE_INTEGER) }
                        Ufc.PartitionKey.KindCase.SHARD_MD5 -> {
                            val shard = partition.shardMd5
                            checkRule(shard.totalShards in 1..MAX_SAFE_INTEGER)
                            val attribute = at(configuration.attributesList, shard.attributeIndex)
                            val value = attributeValue(shard.attributeIndex, request.context)
                                ?: throw EvaluationFailure(
                                    if (attribute.hasTargetingKey()) "TARGETING_KEY_MISSING" else "INVALID_CONTEXT"
                                )
                            val text = stringValue(value) ?: throw EvaluationFailure("INVALID_CONTEXT")
                            // MD5 and the first four bytes in big-endian order are the assignment protocol.
                            val digest = MessageDigest.getInstance("MD5").digest((shard.salt + text).toByteArray(Charsets.UTF_8))
                            var prefix = 0L
                            for (i in 0..3) prefix = (prefix shl 8) or (digest[i].toLong() and 255)
                            prefix % shard.totalShards
                        }
                        else -> throw EvaluationFailure("PARSE_ERROR")
                    }
                }
                for (split in allocation.splitsList) {
                    checkRule(split.rangesCount == coordinates.size)
                    if (!coordinates.zip(split.rangesList).all { (coordinate, range) ->
                        checkRule(!range.hasFrom() || range.from in 0..MAX_SAFE_INTEGER)
                        checkRule(!range.hasTo() || range.to in 0..MAX_SAFE_INTEGER)
                        (!range.hasFrom() || coordinate >= range.from) && (!range.hasTo() || coordinate < range.to)
                    }) continue
                    val variation = at(flag.variationsList, split.variationIndex)
                    checkRule(variation.hasBooleanValue())
                    metadata["__dd_allocation_key"] = allocation.key
                    metadata["allocationKey"] = allocation.key
                    metadata["__dd_do_log"] = allocation.logExposureEvent
                    metadata["doLog"] = allocation.logExposureEvent
                    metadata["variationType"] = "boolean"
                    if (split.hasSerialId()) metadata["__dd_split_serial_id"] = split.serialId
                    val reason = when (split.reason) {
                        Ufc.Reason.REASON_TARGETING_MATCH -> "TARGETING_MATCH"
                        Ufc.Reason.REASON_SPLIT -> "SPLIT"
                        Ufc.Reason.REASON_STATIC -> "STATIC"
                        Ufc.Reason.REASON_DEFAULT -> "DEFAULT"
                        else -> "UNKNOWN"
                    }
                    return mapOf(
                        "value" to variation.booleanValue, "reason" to reason,
                        "variant" to at(configuration.stringsList, variation.keyStringIndex), "flagMetadata" to metadata
                    )
                }
            }
            fallback("DEFAULT")
        } catch (failure: EvaluationFailure) {
            fallback("ERROR", failure.code).also {
                if (failure.code == "PARSE_ERROR") {
                    it["errorMessage"] = "Invalid or unsupported client-protobuf benchmark workload"
                }
            }
        }
    }

    private fun matches(index: Int, context: Map<String, Any?>, cache: MutableMap<Int, Boolean>): Boolean {
        cache[index]?.let { return it }
        val condition = at(configuration.conditionsList, index)
        val result = when (condition.kindCase) {
            Ufc.Condition.KindCase.ALL -> condition.all.conditionIndexesList.all {
                checkRule(it >= 0 && it < index); matches(it, context, cache)
            }
            Ufc.Condition.KindCase.ANY -> condition.any.conditionIndexesList.any {
                checkRule(it >= 0 && it < index); matches(it, context, cache)
            }
            Ufc.Condition.KindCase.STRING_MEMBERSHIP -> {
                val membership = condition.stringMembership
                val text = stringValue(attributeValue(membership.attributeIndex, context))
                if (text == null) false else {
                    val included = membership.stringIndexesList.any { at(configuration.stringsList, it) == text }
                    if (membership.negate) !included else included
                }
            }
            Ufc.Condition.KindCase.NUMERIC -> {
                val numeric = condition.numeric
                val value = numberValue(attributeValue(numeric.attributeIndex, context))
                if (value == null) false else when (numeric.comparator) {
                    Ufc.NumericComparator.NUMERIC_COMPARATOR_LESS_THAN -> value < numeric.comparand
                    Ufc.NumericComparator.NUMERIC_COMPARATOR_LESS_THAN_OR_EQUAL -> value <= numeric.comparand
                    Ufc.NumericComparator.NUMERIC_COMPARATOR_GREATER_THAN -> value > numeric.comparand
                    Ufc.NumericComparator.NUMERIC_COMPARATOR_GREATER_THAN_OR_EQUAL -> value >= numeric.comparand
                    else -> throw EvaluationFailure("PARSE_ERROR")
                }
            }
            Ufc.Condition.KindCase.ATTRIBUTE_PRESENCE -> {
                val presence = condition.attributePresence
                val absent = attributeValue(presence.attributeIndex, context) == null
                if (presence.expectNull) absent else !absent
            }
            else -> throw EvaluationFailure("PARSE_ERROR")
        }
        cache[index] = result
        return result
    }

    private fun attributeValue(index: Int, context: Map<String, Any?>): Any? {
        val reference = at(configuration.attributesList, index)
        if (reference.hasTargetingKey()) return context["targetingKey"]
        checkRule(reference.hasAttributePath())
        val path = reference.attributePath.segmentsList
        checkRule(path.isNotEmpty() && path[0].hasObjectKeyStringIndex())
        var value: Any? = context
        for (segment in path) {
            value = when (segment.kindCase) {
                Ufc.AttributePathSegment.KindCase.OBJECT_KEY_STRING_INDEX ->
                    (value as? Map<*, *>)?.get(at(configuration.stringsList, segment.objectKeyStringIndex))
                Ufc.AttributePathSegment.KindCase.ARRAY_INDEX -> (value as? List<*>)?.getOrNull(segment.arrayIndex)
                else -> throw EvaluationFailure("PARSE_ERROR")
            }
        }
        return value
    }

    private fun <T> at(values: List<T>, index: Int): T {
        checkRule(index in values.indices)
        return values[index]
    }

    private fun checkRule(valid: Boolean) {
        if (!valid) throw EvaluationFailure("PARSE_ERROR")
    }

    private fun stringValue(value: Any?): String? = when (value) {
        is String, is Boolean -> value.toString()
        is Number -> value.toDouble().let { number ->
            when {
                !number.isFinite() -> null
                number == 0.0 -> "0"
                else -> number.toString().removeSuffix(".0")
            }
        }
        else -> null
    }

    private fun numberValue(value: Any?): Double? = when (value) {
        is Number -> value.toDouble().takeIf { it.isFinite() }
        is String -> value.takeIf { DECIMAL.matches(it) }?.toDoubleOrNull()?.takeIf { it.isFinite() }
        else -> null
    }

    companion object {
        private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L
        private val DECIMAL = Regex("^[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?$")
    }
}
