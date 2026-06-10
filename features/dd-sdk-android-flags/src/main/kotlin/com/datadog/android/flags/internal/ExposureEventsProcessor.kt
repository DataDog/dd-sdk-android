/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import androidx.collection.LruCache
import com.datadog.android.flags.internal.storage.RecordWriter
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.ExposureEvent
import com.datadog.android.flags.model.UnparsedFlag
import com.datadog.android.internal.time.TimeProvider

internal class ExposureEventsProcessor(private val writer: RecordWriter, private val timeProvider: TimeProvider) :
    EventsProcessor {

    private data class CacheKey(
        val targetingKey: String,
        val flagKey: String
    )

    private data class CacheValue(
        val allocationKey: String,
        val variationKey: String
    )

    @Suppress("UnsafeThirdPartyFunctionCall") // maxSize > 0
    private val exposuresSentCache = LruCache<CacheKey, CacheValue>(MAX_CACHE_ENTRIES)

    override fun processEvent(flagKey: String, context: EvaluationContext, data: UnparsedFlag) {
        val cacheKey = CacheKey(
            targetingKey = context.targetingKey,
            flagKey = flagKey
        )
        val cacheValue = CacheValue(
            allocationKey = data.allocationKey,
            variationKey = data.variationKey
        )

        val shouldWrite = synchronized(exposuresSentCache) {
            val lastSentValue = exposuresSentCache[cacheKey]
            if (lastSentValue != cacheValue) {
                @Suppress("UnsafeThirdPartyFunctionCall") // safe - non-null key and value
                exposuresSentCache.put(cacheKey, cacheValue)
                true
            } else {
                false
            }
        }

        if (shouldWrite) {
            val event = buildExposureEvent(flagKey, context, data)
            writeExposureEvent(event)
        }
    }

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
            )
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
