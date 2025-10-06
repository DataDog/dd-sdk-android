/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import androidx.collection.LruCache
import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import com.datadog.android.flags.featureflags.model.EvaluationContext
import com.datadog.android.flags.internal.model.ExposureEvent
import com.datadog.android.flags.internal.model.Identifier
import com.datadog.android.flags.internal.model.Subject
import com.datadog.android.flags.internal.storage.RecordWriter

internal class ExposureEventsProcessor(
    private val writer: RecordWriter
) : EventsProcessor {

    private data class CacheKey(
        val targetingKey: String,
        val flagName: String,
        val allocationKey: String,
        val variationKey: String
    )

    @Suppress("UnsafeThirdPartyFunctionCall") // maxSize > 0
    private val exposuresSentCache = LruCache<CacheKey, Boolean>(MAX_CACHE_SIZE)

    override fun processEvent(flagName: String, context: EvaluationContext, data: PrecomputedFlag) {
        val cacheKey = CacheKey(
            targetingKey = context.targetingKey,
            flagName = flagName,
            allocationKey = data.allocationKey,
            variationKey = data.variationKey
        )

        @Suppress("UnsafeThirdPartyFunctionCall") // LruCache.get() is safe with non-null key
        val alreadySent = exposuresSentCache[cacheKey]

        if (alreadySent == null) {
            @Suppress("UnsafeThirdPartyFunctionCall") // LruCache.put() is safe with non-null key
            exposuresSentCache.put(cacheKey, true)
            val event = buildExposureEvent(flagName, context, data)
            writeExposureEvent(event)
        }
    }

    private fun buildExposureEvent(
        flagName: String,
        context: EvaluationContext,
        data: PrecomputedFlag
    ): ExposureEvent {
        val now = System.currentTimeMillis()
        return ExposureEvent(
            timeStamp = now,
            allocation = Identifier(data.allocationKey),
            flag = Identifier(flagName),
            variant = Identifier(data.variationKey),
            subject = Subject(
                id = context.targetingKey,
                attributes = context.attributes.mapValues { it.value }
            )
        )
    }

    private fun writeExposureEvent(record: ExposureEvent) {
        writer.write(record)
    }

    companion object {
        private const val MAX_CACHE_SIZE = 100
    }
}
