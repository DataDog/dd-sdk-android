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
     * Uses double-check locking to avoid spurious flushes when multiple threads
     * reach the threshold simultaneously. The size is checked without a lock first
     * (fast path), then re-checked while holding the write lock before draining.
     *
     * @return list of drained events if threshold was reached, null otherwise
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
    ): List<FlagEvaluation>? {
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
            errorMessage = errorMessage
        )

        mapLock.read {
            @Suppress("UnsafeThirdPartyFunctionCall") // Only throws if null is passed
            val existing = aggregationMap.putIfAbsent(key, stats)
            existing?.recordEvaluation(timestamp, errorMessage)
        }

        if (aggregationMap.size < maxAggregations) {
            return null
        }

        // Re-check while holding exclusive lock to ensure only one thread drains.
        val drained = mapLock.write {
            if (aggregationMap.size < maxAggregations) null else swapMap()
        }
        return drained?.map { (_, stats) -> stats.toEvaluationEvent() }
    }

    /**
     * Atomically drains all aggregated events, returning them as [FlagEvaluation] objects.
     * The internal map is cleared after this call.
     *
     * @return list of aggregated events, or empty list if none
     */
    fun drain(): List<FlagEvaluation> {
        val drained = mapLock.write { swapMap() }
        return drained?.map { (_, stats) -> stats.toEvaluationEvent() } ?: emptyList()
    }

    /**
     * Swaps the aggregation map with a fresh one and returns the old map.
     * Must be called while holding the write lock.
     *
     * @return the old map, or null if empty
     */
    private fun swapMap(): Map<AggregationKey, AggregationStats>? {
        if (aggregationMap.isEmpty()) {
            return null
        }
        val toDrain = aggregationMap
        aggregationMap = ConcurrentHashMap()
        return toDrain
    }
}
