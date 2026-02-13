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
        val flagName: String,
        val allocationKey: String,
        val variationKey: String
    )

    @Suppress("UnsafeThirdPartyFunctionCall") // maxSize > 0
    private val exposuresSentCache = object : LruCache<CacheKey, Boolean>(MAX_CACHE_SIZE_BYTES) {
        override fun sizeOf(key: CacheKey, value: Boolean): Int {
            // Calculate approximate memory footprint of the cache entry
            // String overhead: ~40 bytes + (2 bytes per character for UTF-16)
            // Object overhead for CacheKey: ~16 bytes
            // Boolean: ~1 byte
            val keySize = OBJECT_OVERHEAD +
                (STRING_OVERHEAD + key.targetingKey.length * CHAR_SIZE) +
                (STRING_OVERHEAD + key.flagName.length * CHAR_SIZE) +
                (STRING_OVERHEAD + key.allocationKey.length * CHAR_SIZE) +
                (STRING_OVERHEAD + key.variationKey.length * CHAR_SIZE)
            val valueSize = BOOLEAN_SIZE
            return keySize + valueSize
        }
    }

    override fun processEvent(flagName: String, context: EvaluationContext, data: UnparsedFlag) {
        val cacheKey = CacheKey(
            targetingKey = context.targetingKey,
            flagName = flagName,
            allocationKey = data.allocationKey,
            variationKey = data.variationKey
        )

        // Atomically check and mark to prevent duplicate writes
        // Only write to cache on first access to avoid refreshing LRU position
        val isFirstTime = synchronized(exposuresSentCache) {
            val alreadySent = exposuresSentCache[cacheKey]
            if (alreadySent == null) {
                exposuresSentCache.put(cacheKey, true)
                true
            } else {
                false
            }
        }

        if (isFirstTime) {
            val event = buildExposureEvent(flagName, context, data)
            writeExposureEvent(event)
        }
    }

    private fun buildExposureEvent(flagName: String, context: EvaluationContext, data: UnparsedFlag): ExposureEvent {
        val now = timeProvider.getDeviceTimestampMillis()
        return ExposureEvent(
            timestamp = now,
            allocation = ExposureEvent.Identifier(data.allocationKey),
            flag = ExposureEvent.Identifier(flagName),
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
        // Maximum cache size in bytes (4MB)
        private const val MAX_CACHE_SIZE_BYTES = 4 * 1024 * 1024 // 4MB

        // Memory overhead constants for size calculation
        private const val OBJECT_OVERHEAD = 16 // bytes for object header
        private const val STRING_OVERHEAD = 40 // bytes for String object overhead
        private const val CHAR_SIZE = 2 // bytes per character (UTF-16)
        private const val BOOLEAN_SIZE = 1 // byte
    }
}
