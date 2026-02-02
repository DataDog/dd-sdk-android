/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.aggregation

import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagEvaluation
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe aggregator for flag evaluation events.
 *
 * Uses a [ReentrantReadWriteLock] to allow concurrent [record] calls while
 * ensuring exclusive access during [drain].
 */
internal class EvaluationAggregator(
    private val maxAggregations: Int
) {
    @Volatile
    private var aggregationMap = ConcurrentHashMap<AggregationKey, AggregationStats>()

    private val mapLock = ReentrantReadWriteLock()

    /**
     * Records a flag evaluation. Concurrent calls are allowed.
     *
     * @return true if the aggregation count has reached [maxAggregations]
     */
    fun record(
        timestamp: Long,
        flagKey: String,
        context: EvaluationContext,
        service: String?,
        rumApplicationId: String?,
        rumViewName: String?,
        variantKey: String?,
        allocationKey: String?,
        reason: String?,
        errorCode: String?,
        errorMessage: String?
    ): Boolean {
        val key = AggregationKey(
            flagKey = flagKey,
            variantKey = variantKey,
            allocationKey = allocationKey,
            targetingKey = context.targetingKey,
            viewName = rumViewName,
            errorCode = errorCode
        )

        val stats = AggregationStats(
            aggregationKey = key,
            firstTimestamp = timestamp,
            context = context,
            service = service,
            rumApplicationId = rumApplicationId,
            rumViewName = rumViewName,
            reason = reason,
            errorMessage = errorMessage
        )

        return mapLock.read {
            @Suppress("UnsafeThirdPartyFunctionCall") // Only throws if null is passed
            val existing = aggregationMap.putIfAbsent(key, stats)
            existing?.recordEvaluation(timestamp, errorMessage)
            aggregationMap.size >= maxAggregations
        }
    }

    /**
     * Atomically drains all aggregated events, returning them as [FlagEvaluation] objects.
     * The internal map is cleared after this call.
     *
     * @return list of aggregated events, or empty list if none
     */
    fun drain(): List<FlagEvaluation> {
        val snapshot = mapLock.write {
            if (aggregationMap.isEmpty()) {
                return@write null
            }
            val entriesToDrain = aggregationMap
            aggregationMap = ConcurrentHashMap()
            entriesToDrain
        }

        return snapshot?.map { (_, stats) -> stats.toEvaluationEvent() } ?: emptyList()
    }
}
