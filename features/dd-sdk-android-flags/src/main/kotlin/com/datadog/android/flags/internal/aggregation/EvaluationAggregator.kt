/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.aggregation

import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagEvaluation
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Thread-safe aggregator for flag evaluation events.
 *
 * Uses a [ReentrantLock] to allow concurrent [record] calls while
 * ensuring exclusive access during [drain].
 */
internal class EvaluationAggregator(private val maxAggregations: Int) {
    private var aggregationMap = mutableMapOf<AggregationKey, AggregationStats>()

    private val mapLock = ReentrantLock()

    /**
     * Records a flag evaluation. Concurrent calls are allowed.
     *
     * Drains the aggregation map if the size exceeds the threshold and returns the drained events.
     * @return list of drained events if threshold was reached, empty otherwise
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
        errorCode: String?,
        errorMessage: String?
    ): List<FlagEvaluation> {
        val key = AggregationKey(
            flagKey = flagKey,
            variantKey = variantKey,
            allocationKey = allocationKey,
            targetingKey = context.targetingKey,
            viewName = rumViewName,
            errorCode = errorCode
        )

        val drained = mapLock.withLock {
            @Suppress("UnsafeThirdPartyFunctionCall") // Only throws if null is passed
            val existing = aggregationMap.get(key) ?: AggregationStats(
                aggregationKey = key,
                count = 0,
                firstEvaluation = timestamp,
                lastEvaluation = timestamp,
                context = context,
                service = service,
                rumApplicationId = rumApplicationId,
                errorMessage = errorMessage
            )
            @Suppress("UnsafeThirdPartyFunctionCall") // safe - non-null key and value
            aggregationMap.put(
                key,
                existing.copy(
                    count = existing.count + 1,
                    firstEvaluation = minOf(existing.firstEvaluation, timestamp),
                    lastEvaluation = maxOf(existing.lastEvaluation, timestamp),
                    errorMessage = errorMessage
                )
            )

            if (aggregationMap.size < maxAggregations) emptyMap() else drainAggregationStats()
        }

        return drained.map { it.value.toEvaluationEvent() }
    }

    /**
     * Atomically drains all aggregated events, returning them as [FlagEvaluation] objects.
     * The internal map is cleared after this call.
     *
     * @return list of aggregated events, or empty list if none
     */
    fun drain(): List<FlagEvaluation> {
        val drained = mapLock.withLock { drainAggregationStats() }
        return drained.map { it.value.toEvaluationEvent() }
    }

    /**
     * Swaps the aggregation map with a fresh one and returns the old map.
     * Must be called while holding the write lock.
     *
     * @return the old map, or null if empty
     */
    private fun drainAggregationStats(): Map<AggregationKey, AggregationStats> = mapLock.withLock {
        val toDrain = aggregationMap
        aggregationMap = mutableMapOf()
        toDrain
    }
}
