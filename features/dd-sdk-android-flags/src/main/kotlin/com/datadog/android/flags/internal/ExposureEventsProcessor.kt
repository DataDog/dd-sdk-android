/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import androidx.collection.LruCache
import com.datadog.android.flags.internal.model.ExposureEvent
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.model.SplitFeature
import com.datadog.android.flags.internal.model.forDestination
import com.datadog.android.flags.internal.storage.RecordWriter
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.UnparsedFlag
import com.datadog.android.internal.time.TimeProvider

/**
 * @param writer receives the exposure events to send.
 * @param timeProvider provides event timestamps.
 * @param writeEvents false to record exposures in the cache without building or writing events.
 */
internal class ExposureEventsProcessor(
    private val writer: RecordWriter,
    private val timeProvider: TimeProvider,
    private val writeEvents: Boolean = true
) : EventsProcessor {

    private data class CacheKey(
        val targetingKey: String,
        val flagKey: String
    )

    private data class CacheValue(
        val allocationKey: String,
        val variationKey: String,
        val serialId: Long?
    )

    @Suppress("UnsafeThirdPartyFunctionCall") // maxSize > 0
    private val exposuresSentCache = LruCache<CacheKey, CacheValue>(MAX_CACHE_ENTRIES)

    override fun processEvent(flagKey: String, context: EvaluationContext, data: UnparsedFlag): Boolean {
        val cacheKey = CacheKey(targetingKey = context.targetingKey, flagKey = flagKey)
        val cacheValue = data.toCacheValue()

        synchronized(exposuresSentCache) {
            val isRecorded = exposuresSentCache[cacheKey] == cacheValue
            if (!isRecorded) {
                if (writeEvents) writeExposureEvent(buildExposureEvent(flagKey, context, data))
                @Suppress("UnsafeThirdPartyFunctionCall") // safe - non-null key and value
                exposuresSentCache.put(cacheKey, cacheValue)
            }
            return isRecorded
        }
    }

    private fun UnparsedFlag.toCacheValue() = CacheValue(
        allocationKey = allocationKey,
        variationKey = variationKey,
        serialId = serialId
    )

    private fun buildExposureEvent(flagKey: String, context: EvaluationContext, data: UnparsedFlag): ExposureEvent {
        val now = timeProvider.getDeviceTimestampMillis()
        return ExposureEvent(
            timestamp = now,
            allocation = ExposureEvent.Identifier(data.allocationKey),
            flag = ExposureEvent.Identifier(flagKey),
            variant = ExposureEvent.Identifier(data.variationKey),
            subject = ExposureEvent.Subject(
                id = context.targetingKey,
                attributes = ExposureEvent.Attributes(
                    additionalProperties = context.attributes.toMutableMap()
                )
            ),
            serialId = data.serialId,
            features = (data as? PrecomputedFlag)?.features
                ?.forDestination(SplitFeature.DESTINATION_EXPOSURE)
                ?.takeIf { it.isNotEmpty() }
                ?.let { ExposureEvent.Features(it.toMutableMap<String, Any?>()) }
        )
    }

    private fun writeExposureEvent(record: ExposureEvent) {
        writer.write(record)
    }

    companion object {
        // Supports the expected high-water mark of two subjects evaluating
        // 2,500 flags each. Normal flag keys are typically tens of characters,
        // so entry count is easier to reason about than object-size estimates.
        private const val MAX_CACHE_ENTRIES = 5_000
    }
}
